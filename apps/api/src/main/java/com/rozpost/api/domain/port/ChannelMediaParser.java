package com.rozpost.api.domain.port;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** OCP seam: add FB/TikTok parsers without editing WhatsApp ingest. */
public interface ChannelMediaParser {
    String channel();

    List<InboundMedia> parseMediaMessages(JsonNode root);

    record InboundMedia(
            String channel,
            String externalMessageId,
            String senderExternalId,
            String kind,
            String providerMediaId,
            String mimeType,
            String instructionText,
            String source
    ) {}
}
