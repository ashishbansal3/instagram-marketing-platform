import express from "express";

const app = express();
app.use(express.json({ limit: "2mb" }));

app.get("/health", (_req, res) => {
  res.json({ service: "rozpost-remotion", ok: true });
});

/**
 * Skeleton assemble endpoint.
 * Workers POST still/timeline + caption; Remotion render plugs in later.
 */
app.post("/assemble", (req, res) => {
  const { postId, caption, instructionText } = req.body || {};
  res.status(202).json({
    status: "accepted",
    postId,
    caption: caption || null,
    instructionText: instructionText || null,
    outputKey: postId ? `renders/${postId}/reel-stub.mp4` : null,
    note: "RozPost Remotion stub — replace with real render pipeline"
  });
});

const port = Number(process.env.PORT || 3100);
if (process.env.NODE_ENV !== "test") {
  app.listen(port, "0.0.0.0", () => {
    console.log(`rozpost remotion listening on ${port}`);
  });
}

export default app;
