import React from "react";
import { Composition } from "remotion";
import { CaptionReel, CaptionReelProps } from "./CaptionReel";

export const RemotionRoot: React.FC = () => {
  const defaults: CaptionReelProps = {
    mediaSrc: "https://via.placeholder.com/1080x1920.jpg",
    mediaType: "image",
    caption: "नई ऑफर — आज ही बुक करें",
    brandName: "RozPost",
    locale: "hi-IN",
    instructionText: "",
  };

  return (
    <Composition
      id="CaptionReel"
      component={CaptionReel}
      durationInFrames={125}
      fps={25}
      width={1080}
      height={1920}
      defaultProps={defaults}
    />
  );
};
