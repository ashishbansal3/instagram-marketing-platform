package com.rozpost.api.infrastructure.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.rozpost.api.domain.port.ChannelMediaParser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class WhatsAppChannelMediaParser implements ChannelMediaParser {

    @Override
    public String channel() {
        return "whatsapp";
    }

    @Override
    public List<InboundMedia> parseMediaMessages(JsonNode root) {
        List<InboundMedia> out = new ArrayList<>();
        if (root == null || root.isMissingNode()) {
            return out;
        }
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode messages = change.path("value").path("messages");
                if (!messages.isArray()) {
                    continue;
                }
                for (JsonNode message : messages) {
                    String type = text(message, "type").orElse("");
                    if (!"image".equals(type) && !"video".equals(type)) {
                        continue;
                    }
                    JsonNode mediaNode = "video".equals(type) ? message.path("video") : message.path("image");
                    out.add(new InboundMedia(
                            channel(),
                            text(message, "id").orElse(""),
                            text(message, "from").orElse(""),
                            "video".equals(type) ? "video" : "image",
                            text(mediaNode, "id").orElse(null),
                            text(mediaNode, "mime_type")
                                    .orElse("video".equals(type) ? "video/mp4" : "image/jpeg"),
                            text(mediaNode, "caption").orElse(null),
                            "whatsapp"
                    ));
                }
            }
        }
        return out;
    }

    private static Optional<String> text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return Optional.empty();
        }
        String s = v.asText();
        return s == null || s.isBlank() ? Optional.empty() : Optional.of(s);
    }
}
