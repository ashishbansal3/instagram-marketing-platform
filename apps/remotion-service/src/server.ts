import cors from "cors";
import express from "express";
import fs from "fs";
import multer from "multer";
import os from "os";
import path from "path";
import { spawnSync } from "child_process";

const PORT = Number(process.env.REMOTION_PORT || process.env.PORT || 3100);
const INTERNAL_TOKEN = (process.env.REMOTION_INTERNAL_TOKEN || process.env.INTERNAL_API_TOKEN || "").trim();
// Prefer APP_ENV — npm test / Next often set NODE_ENV=production without meaning prod deploy.
const APP_ENV = (process.env.APP_ENV || "local").trim().toLowerCase();
/** Production/staging must never run with an empty internal token (local/e2e keep open auth). */
const REQUIRE_AUTH =
  process.env.REMOTION_REQUIRE_AUTH === "true" ||
  APP_ENV === "production" ||
  APP_ENV === "staging" ||
  APP_ENV === "prod";
const MAX_CONCURRENT = Number(process.env.REMOTION_MAX_CONCURRENT || 2);
const RENDER_TIMEOUT_MS = Number(process.env.REMOTION_RENDER_TIMEOUT_MS || 120_000);

if (REQUIRE_AUTH && !INTERNAL_TOKEN) {
  // Fail closed before accepting traffic — ECS health will stay unhealthy until secret is wired.
  console.error(
    JSON.stringify({
      ts: new Date().toISOString(),
      level: "error",
      service: "rozpost-remotion",
      msg: "fatal_missing_internal_token",
      appEnv: APP_ENV,
      hint: "Set REMOTION_INTERNAL_TOKEN or INTERNAL_API_TOKEN (or APP_ENV=local for dogfood)",
    }),
  );
  process.exit(1);
}

const app = express();
const upload = multer({ dest: path.join(os.tmpdir(), "remotion-uploads") });

let inflight = 0;
let shuttingDown = false;

function log(level: "info" | "warn" | "error", msg: string, fields: Record<string, unknown> = {}) {
  const line = JSON.stringify({
    ts: new Date().toISOString(),
    level,
    service: "rozpost-remotion",
    msg,
    ...fields,
  });
  if (level === "error") console.error(line);
  else if (level === "warn") console.warn(line);
  else console.log(line);
}

app.use(cors());
app.use(express.json({ limit: "2mb" }));

const fontsDir = path.join(__dirname, "..", "public", "fonts");
app.use("/fonts", express.static(fontsDir));
app.use("/preview", express.static(path.join(os.tmpdir(), "remotion-previews"), { fallthrough: true }));

function requireInternalAuth(req: express.Request, res: express.Response, next: express.NextFunction) {
  if (!INTERNAL_TOKEN) {
    // Local / META_MODE=mock dogfood only — REQUIRE_AUTH exits at boot if empty in prod.
    return next();
  }
  const header = String(req.header("X-Internal-Token") || "");
  if (header !== INTERNAL_TOKEN) {
    res.status(401).json({ error: "unauthorized", code: "INTERNAL_AUTH_REQUIRED" });
    return;
  }
  next();
}

app.get("/health", (_req, res) => {
  res.json({
    status: shuttingDown ? "draining" : "ok",
    ok: !shuttingDown,
    service: "rozpost-remotion",
    inflight,
    maxConcurrent: MAX_CONCURRENT,
    fonts: fs.existsSync(fontsDir) ? fs.readdirSync(fontsDir) : [],
  });
});

type RenderBody = {
  caption?: string;
  locale?: string;
  instructionText?: string;
  brandName?: string;
};

async function tryRemotionRender(
  inputPath: string,
  outPath: string,
  props: Required<RenderBody> & { mediaType: "image" | "video" },
) {
  const { bundle } = await import("@remotion/bundler");
  const { renderMedia, selectComposition } = await import("@remotion/renderer");
  const entry = path.join(__dirname, "index.js");
  const entryTs = path.join(__dirname, "..", "src", "index.ts");
  const entryPoint = fs.existsSync(entry) ? entry : entryTs;

  const serveUrl = await bundle({ entryPoint });
  const mediaSrc = `file://${inputPath}`;
  const inputProps = {
    mediaSrc,
    mediaType: props.mediaType,
    caption: props.caption,
    brandName: props.brandName,
    locale: props.locale,
    instructionText: props.instructionText,
  };
  const composition = await selectComposition({
    serveUrl,
    id: "CaptionReel",
    inputProps,
  });
  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation: outPath,
    inputProps,
  });
}

function ffmpegCaptionFallback(
  inputPath: string,
  outPath: string,
  caption: string,
  brandName: string,
  locale: string,
) {
  const fontFile = locale.startsWith("pa")
    ? path.join(fontsDir, "NotoSansGurmukhi-Bold.ttf")
    : path.join(fontsDir, "NotoSansDevanagari-Bold.ttf");
  const fontOpt = fs.existsSync(fontFile) ? `:fontfile=${fontFile}` : "";
  const esc = (s: string) => s.replace(/\\/g, "\\\\").replace(/:/g, "\\:").replace(/'/g, "");
  const vf =
    `scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,` +
    `drawtext=text='${esc(brandName)}'${fontOpt}:fontsize=48:fontcolor=white:x=(w-text_w)/2:y=h-260:box=1:boxcolor=black@0.55:boxborderw=18,` +
    `drawtext=text='${esc(caption)}'${fontOpt}:fontsize=36:fontcolor=white:x=(w-text_w)/2:y=h-160:box=1:boxcolor=black@0.45:boxborderw=14`;
  const isVideo = /\.(mp4|mov|webm|mkv)$/i.test(inputPath);
  const args = isVideo
    ? ["-y", "-i", inputPath, "-vf", vf, "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an", outPath]
    : ["-y", "-loop", "1", "-i", inputPath, "-vf", vf, "-t", "5", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-an", outPath];
  const result = spawnSync("ffmpeg", args, { encoding: "utf8", timeout: RENDER_TIMEOUT_MS });
  if (result.status !== 0) {
    throw new Error(`ffmpeg fallback failed: ${result.stderr?.slice(-800)}`);
  }
}

app.post("/assemble", requireInternalAuth, (req, res) => {
  res.status(202).json({
    status: "accepted",
    note: "Use multipart POST /v1/render for reel assembly",
    postId: (req.body || {}).postId || null,
    instructionText: (req.body || {}).instructionText || null,
  });
});

app.post(["/v1/render", "/render"], requireInternalAuth, upload.single("media"), async (req, res) => {
  const correlationId = String(req.header("X-Correlation-Id") || req.body?.correlationId || "");
  if (shuttingDown) {
    res.status(503).json({ error: "draining", code: "SHUTTING_DOWN", correlationId });
    return;
  }
  if (inflight >= MAX_CONCURRENT) {
    res.status(429).json({ error: "max concurrent renders", code: "RENDER_BUSY", correlationId });
    return;
  }
  if (!req.file) {
    res.status(400).json({ error: "multipart field 'media' required", code: "VALIDATION", correlationId });
    return;
  }

  inflight += 1;
  try {
    const caption = String(req.body.caption || "नई ऑफर — आज ही बुक करें").slice(0, 200);
    const locale = String(req.body.locale || "hi-IN").slice(0, 16);
    const instructionText = String(req.body.instructionText || "").slice(0, 500);
    const brandName = String(req.body.brandName || "RozPost").slice(0, 80);
    const mime = (req.file.mimetype || "").toLowerCase();
    const nameHint = `${req.file.originalname || ""} ${req.file.path}`;
    const mediaType: "image" | "video" =
      mime.startsWith("video/") || /\.(mp4|mov|webm|mkv)$/i.test(nameHint) ? "video" : "image";

    const ext =
      mediaType === "video"
        ? path.extname(req.file.originalname || "") || ".mp4"
        : path.extname(req.file.originalname || "") || ".jpg";
    const typedInput = `${req.file.path}${ext}`;
    fs.renameSync(req.file.path, typedInput);
    req.file.path = typedInput;

    // Allowlist: local temp uploads only (no arbitrary remote URL fetch in this service).
    if (!typedInput.startsWith(os.tmpdir())) {
      res.status(400).json({ error: "invalid media path", code: "VALIDATION", correlationId });
      return;
    }

    const previewDir = path.join(os.tmpdir(), "remotion-previews");
    fs.mkdirSync(previewDir, { recursive: true });
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const outPath = path.join(previewDir, `${id}.mp4`);

    let engine = "remotion";
    try {
      await Promise.race([
        tryRemotionRender(typedInput, outPath, {
          caption,
          locale,
          instructionText,
          brandName,
          mediaType,
        }),
        new Promise((_, reject) =>
          setTimeout(() => reject(new Error("render timeout")), RENDER_TIMEOUT_MS),
        ),
      ]);
    } catch (err) {
      log("warn", "remotion_render_failed_fallback_ffmpeg", {
        error: (err as Error).message,
        correlationId,
      });
      engine = "ffmpeg-noto-fallback";
      ffmpegCaptionFallback(typedInput, outPath, caption, brandName, locale);
    }

    const previewUrl = `http://localhost:${PORT}/preview/${id}.mp4`;
    res.setHeader("X-Preview-Url", previewUrl);
    res.setHeader("X-Render-Engine", engine);
    res.setHeader("X-Correlation-Id", correlationId);
    res.setHeader("Content-Type", "video/mp4");
    res.sendFile(outPath);
  } catch (e) {
    log("error", "render_failed", { error: (e as Error).message, correlationId });
    res.status(500).json({ error: (e as Error).message, code: "RENDER_FAILED", correlationId });
  } finally {
    inflight = Math.max(0, inflight - 1);
    if (req.file?.path) fs.unlink(req.file.path, () => undefined);
  }
});

const server = app.listen(PORT, () => {
  log("info", "listening", {
    port: PORT,
    maxConcurrent: MAX_CONCURRENT,
    auth: Boolean(INTERNAL_TOKEN),
    requireAuth: REQUIRE_AUTH,
    appEnv: APP_ENV,
  });
});

function shutdown(signal: string) {
  shuttingDown = true;
  log("info", "shutdown_started", { signal, inflight });
  server.close(() => {
    log("info", "shutdown_complete", { signal });
    process.exit(0);
  });
  setTimeout(() => process.exit(1), RENDER_TIMEOUT_MS + 5_000).unref();
}

process.on("SIGTERM", () => shutdown("SIGTERM"));
process.on("SIGINT", () => shutdown("SIGINT"));
