import test from "node:test";
import assert from "node:assert/strict";
import {
  loadFixture,
  extractInstructionText,
  parseInteractiveDecision,
  buildApprovalInteractive,
  createMetaClients
} from "../src/index.js";

test("extract instruction_text from image caption", () => {
  const payload = loadFixture("whatsapp/inbound-image-with-instruction.json");
  const text = extractInstructionText(payload);
  assert.match(text, /peach/i);
});

test("missing caption yields null instruction", () => {
  const payload = loadFixture("whatsapp/inbound-image-no-caption.json");
  assert.equal(extractInstructionText(payload), null);
});

test("Approve / Disapprove interactive parsing", () => {
  const approve = parseInteractiveDecision(loadFixture("whatsapp/interactive-approve.json"));
  assert.equal(approve.decision, "approved");
  const deny = parseInteractiveDecision(loadFixture("whatsapp/interactive-disapprove.json"));
  assert.equal(deny.decision, "disapproved");
});

test("mock IG publish sandbox stubs", () => {
  const { instagram, whatsapp } = createMetaClients("mock");
  const container = instagram.createContainer({ imageUrl: "https://x/a.jpg", caption: "hi" });
  const published = instagram.publish(container.id);
  assert.ok(published.permalink.includes("instagram.com"));
  const interactive = buildApprovalInteractive({
    to: "919876543210",
    postId: "POST1",
    previewImageUrl: "https://x/p.jpg"
  });
  whatsapp.sendInteractive(interactive);
  assert.equal(whatsapp.outbound.length, 1);
});
