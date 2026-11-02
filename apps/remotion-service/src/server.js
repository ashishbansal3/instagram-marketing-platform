import express from "express";

const app = express();
app.use(express.json({ limit: "2mb" }));

const INTERNAL_TOKEN = process.env.INTERNAL_API_TOKEN || "";

function requireInternal(req, res, next) {
  if (!INTERNAL_TOKEN) {
    // local/dev without token: allow but warn once
    return next();
  }
  const hdr = req.header("X-Internal-Token") || "";
  if (hdr !== INTERNAL_TOKEN) {
    return res.status(401).json({ code: "unauthorized", message: "invalid internal token" });
  }
  return next();
}

app.use((req, res, next) => {
  const corr = req.header("X-Correlation-Id") || `remotion-${Date.now()}`;
  res.setHeader("X-Correlation-Id", corr);
  req.correlationId = corr;
  next();
});

app.get("/health", (_req, res) => {
  res.json({ service: "rozpost-remotion", ok: true, appEnv: process.env.APP_ENV || "local" });
});

/**
 * Assemble captions/reel. Workers POST still/timeline + caption.
 * No Meta tokens accepted in this service.
 */
app.post("/assemble", requireInternal, (req, res) => {
  const { postId, caption, instructionText, templateId } = req.body || {};
  if (!postId) {
    return res.status(400).json({ code: "bad_request", message: "postId required" });
  }
  res.status(202).json({
    status: "accepted",
    postId,
    templateId: templateId || "default-9x16",
    caption: caption || null,
    instructionText: instructionText || null,
    outputKey: `renders/${postId}/reel-stub.mp4`,
    correlationId: req.correlationId,
    note: "RozPost Remotion service — wire real Remotion render pipeline"
  });
});

function shutdown() {
  console.log("rozpost remotion shutting down");
  process.exit(0);
}
process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

const port = Number(process.env.PORT || 3100);
if (process.env.NODE_ENV !== "test") {
  app.listen(port, "0.0.0.0", () => {
    console.log(`rozpost remotion listening on ${port}`);
  });
}

export default app;
