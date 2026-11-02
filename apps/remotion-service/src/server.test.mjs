import test from "node:test";
import assert from "node:assert/strict";

test("caption reel module exists", async () => {
  const fs = await import("node:fs");
  assert.equal(fs.existsSync(new URL("./CaptionReel.tsx", import.meta.url)), true);
});
