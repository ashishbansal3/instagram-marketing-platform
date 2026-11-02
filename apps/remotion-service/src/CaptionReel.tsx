import React from "react";
import {
  AbsoluteFill,
  Img,
  OffthreadVideo,
  interpolate,
  useCurrentFrame,
  useVideoConfig,
} from "remotion";

export type CaptionReelProps = {
  mediaSrc: string;
  mediaType: "image" | "video";
  caption: string;
  brandName: string;
  locale: string;
  instructionText: string;
};

const fontFor = (locale: string): string => {
  if (locale.startsWith("pa")) {
    return '"Noto Sans Gurmukhi", "Noto Sans Devanagari", sans-serif';
  }
  if (locale.startsWith("hi") || /[\u0900-\u097F]/.test(locale)) {
    return '"Noto Sans Devanagari", sans-serif';
  }
  // Auto-detect script in caption at render time via CSS stack
  return '"Noto Sans Devanagari", "Noto Sans Gurmukhi", sans-serif';
};

export const CaptionReel: React.FC<CaptionReelProps> = ({
  mediaSrc,
  mediaType,
  caption,
  brandName,
  locale,
  instructionText,
}) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();
  const opacity = interpolate(frame, [0, 12, durationInFrames - 10, durationInFrames], [0, 1, 1, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const scale = interpolate(frame, [0, durationInFrames], [1, 1.06], {
    extrapolateRight: "clamp",
  });
  const fontFamily = fontFor(locale);
  const sub = instructionText?.trim() || caption;

  return (
    <AbsoluteFill style={{ backgroundColor: "#0b1220" }}>
      <AbsoluteFill style={{ transform: `scale(${scale})` }}>
        {mediaType === "video" ? (
          <OffthreadVideo src={mediaSrc} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        ) : (
          <Img src={mediaSrc} style={{ width: "100%", height: "100%", objectFit: "cover" }} />
        )}
      </AbsoluteFill>

      <AbsoluteFill
        style={{
          justifyContent: "flex-end",
          padding: "0 48px 120px",
          opacity,
        }}
      >
        <div
          style={{
            backgroundColor: "rgba(0,0,0,0.55)",
            borderRadius: 18,
            padding: "28px 32px",
            fontFamily,
            color: "white",
          }}
        >
          <div style={{ fontSize: 42, fontWeight: 700, marginBottom: 12 }}>{brandName}</div>
          <div style={{ fontSize: 36, lineHeight: 1.35, fontWeight: 500 }}>{caption}</div>
          {sub !== caption && (
            <div style={{ fontSize: 24, marginTop: 10, opacity: 0.85 }}>{sub}</div>
          )}
        </div>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
