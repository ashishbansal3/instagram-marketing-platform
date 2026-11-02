import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixturesRoot = join(__dirname, "..", "fixtures");

export const META_MODE = process.env.META_MODE || "mock";

export function loadFixture(relPath) {
  const full = join(fixturesRoot, relPath);
  return JSON.parse(readFileSync(full, "utf8"));
}

export function extractInstructionText(waWebhookPayload) {
  const msg = waWebhookPayload?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
  if (!msg) return null;
  if (msg.type === "image") return msg.image?.caption || null;
  if (msg.type === "video") return msg.video?.caption || null;
  if (msg.type === "document") return msg.document?.caption || null;
  if (msg.type === "text") return msg.text?.body || null;
  return null;
}

export function parseInteractiveDecision(waWebhookPayload) {
  const msg = waWebhookPayload?.entry?.[0]?.changes?.[0]?.value?.messages?.[0];
  if (!msg || msg.type !== "interactive") return null;
  const reply = msg.interactive?.button_reply;
  if (!reply?.id) return null;
  if (reply.id.startsWith("approve_")) {
    return { decision: "approved", buttonId: reply.id, title: reply.title, postKey: reply.id.replace(/^approve_post_/, "") };
  }
  if (reply.id.startsWith("disapprove_")) {
    return { decision: "disapproved", buttonId: reply.id, title: reply.title, postKey: reply.id.replace(/^disapprove_post_/, "") };
  }
  return { decision: "unknown", buttonId: reply.id, title: reply.title };
}

export function buildApprovalInteractive({ to, postId, previewImageUrl }) {
  return {
    messaging_product: "whatsapp",
    to,
    type: "interactive",
    interactive: {
      type: "button",
      header: { type: "image", image: { link: previewImageUrl } },
      body: {
        text: "RozPost preview ready. Approve to publish to Instagram, or Disapprove to regenerate (max 3 retries)."
      },
      action: {
        buttons: [
          { type: "reply", reply: { id: `approve_post_${postId}`, title: "Approve" } },
          { type: "reply", reply: { id: `disapprove_post_${postId}`, title: "Disapprove" } }
        ]
      }
    }
  };
}

/** Sandbox stub for IG Content Publishing when META_MODE=mock */
export class MockInstagramPublisher {
  constructor() {
    this.containers = new Map();
    this.published = new Map();
    this.quotaUsage = 3;
  }

  createContainer({ imageUrl, caption }) {
    const id = `IG_CONTAINER_${this.containers.size + 1}`;
    this.containers.set(id, { imageUrl, caption, status: "FINISHED" });
    return { id };
  }

  publish(creationId) {
    const container = this.containers.get(creationId);
    if (!container) throw new Error(`Unknown container ${creationId}`);
    const id = `IG_MEDIA_${this.published.size + 1}`;
    const permalink = `https://www.instagram.com/p/ROZPOST_${id}/`;
    this.published.set(id, { ...container, creationId, permalink });
    this.quotaUsage += 1;
    return { id, permalink };
  }

  contentPublishingLimit() {
    return {
      data: [{ quota_usage: this.quotaUsage, config: { quota_total: 25, quota_duration: 86400 } }]
    };
  }
}

export class MockWhatsAppClient {
  constructor() {
    this.outbound = [];
  }

  sendInteractive(payload) {
    this.outbound.push(payload);
    return { messages: [{ id: `wamid.OUT_${this.outbound.length}` }] };
  }

  getMediaUrl(mediaId) {
    return { url: `https://example.local/rozpost/wa-media/${mediaId}`, mime_type: "image/jpeg" };
  }
}

export function createMetaClients(mode = META_MODE) {
  if (mode === "live") {
    throw new Error("Live Meta clients are not wired in the skeleton; set META_MODE=mock for local META_MODE=mock.");
  }
  return {
    mode: "mock",
    whatsapp: new MockWhatsAppClient(),
    instagram: new MockInstagramPublisher()
  };
}
