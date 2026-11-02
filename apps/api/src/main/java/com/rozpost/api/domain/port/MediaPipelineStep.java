package com.rozpost.api.domain.port;

/** Strategy seam for media pipeline steps (enhance / caption / cut / publish). */
public interface MediaPipelineStep {
    String name();
    void execute(MediaStepContext context);
}
