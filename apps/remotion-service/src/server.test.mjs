import test from "node:test";
import assert from "node:assert/strict";
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(__dirname, "..");

test("noto fonts are bundled", () => {
  const fonts = path.join(root, "public", "fonts");
  assert.equal(fs.existsSync(path.join(fonts, "NotoSansDevanagari-Bold.ttf")), true);
  assert.equal(fs.existsSync(path.join(fonts, "NotoSansGurmukhi-Bold.ttf")), true);
});

test("ffmpeg caption/reel smoke render produces mp4", async () => {
  const ffmpeg = spawnSync("ffmpeg", ["-version"], { encoding: "utf8" });
  assert.equal(ffmpeg.status, 0, "ffmpeg must be installed for remotion CI smoke");

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "rozpost-remotion-"));
  const inputJpg = path.join(tmp, "still.jpg");
  const outMp4 = path.join(tmp, "out.mp4");

  const still = spawnSync(
    "ffmpeg",
    ["-y", "-f", "lavfi", "-i", "color=c=0xF5ECDC:s=640x960:d=1", "-frames:v", "1", inputJpg],
    { encoding: "utf8" },
  );
  assert.equal(still.status, 0, still.stderr);

  const font = path.join(root, "public", "fonts", "NotoSansDevanagari-Bold.ttf");
  const fontOpt = fs.existsSync(font) ? `:fontfile=${font}` : "";
  const vf =
    `scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,` +
    `drawtext=text='RozPost'${fontOpt}:fontsize=48:fontcolor=white:x=(w-text_w)/2:y=h-260:box=1:boxcolor=black@0.55:boxborderw=18`;

  const render = spawnSync(
    "ffmpeg",
    [
      "-y",
      "-loop",
      "1",
      "-i",
      inputJpg,
      "-vf",
      vf,
      "-t",
      "2",
      "-c:v",
      "libx264",
      "-pix_fmt",
      "yuv420p",
      "-an",
      outMp4,
    ],
    { encoding: "utf8" },
  );
  assert.equal(render.status, 0, render.stderr?.slice(-500));
  assert.equal(fs.existsSync(outMp4), true);
  assert.ok(fs.statSync(outMp4).size > 1000, "mp4 too small");
});

test("HTTP /health and /v1/render (ffmpeg fallback) return video", async () => {
  const port = 3100 + Math.floor(Math.random() * 1000);
  const env = {
    ...process.env,
    PORT: String(port),
    REMOTION_PORT: String(port),
    REMOTION_INTERNAL_TOKEN: "",
  };
  const child = spawn("node", ["--import", "tsx", "src/server.ts"], {
    cwd: root,
    env,
    stdio: ["ignore", "pipe", "pipe"],
  });

  const ready = await waitForHealth(port, 30_000);
  assert.equal(ready, true, "remotion /health did not become ready");

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "rozpost-remotion-http-"));
  const inputJpg = path.join(tmp, "still.jpg");
  spawnSync(
    "ffmpeg",
    ["-y", "-f", "lavfi", "-i", "color=c=0xE8DCC8:s=640x960:d=1", "-frames:v", "1", inputJpg],
    { encoding: "utf8" },
  );

  const form = new FormData();
  form.append("media", new Blob([fs.readFileSync(inputJpg)], { type: "image/jpeg" }), "still.jpg");
  form.append("caption", "नई ऑफर");
  form.append("locale", "hi-IN");
  form.append("brandName", "RozPost");
  form.append("instructionText", "soft beige");

  const res = await fetch(`http://127.0.0.1:${port}/v1/render`, {
    method: "POST",
    body: form,
    headers: { "X-Correlation-Id": "remotion-ci-smoke" },
  });
  const buf = Buffer.from(await res.arrayBuffer());
  child.kill("SIGTERM");
  assert.equal(res.status, 200, `render status ${res.status}`);
  assert.match(res.headers.get("content-type") || "", /video\/mp4/);
  assert.ok(buf.length > 1000, "render body too small");
});

async function waitForHealth(port, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const r = await fetch(`http://127.0.0.1:${port}/health`);
      if (r.ok) return true;
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, 400));
  }
  return false;
}
