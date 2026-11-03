package com.rozpost.api.infrastructure.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.port.ChannelMediaParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WhatsAppChannelMediaParserTest {

    @Test
    void parsesImageWithCaption() throws Exception {
        var parser = new WhatsAppChannelMediaParser();
        var root = new ObjectMapper().readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"9198","id":"wamid.1","type":"image",
                  "image":{"id":"m","mime_type":"image/jpeg","caption":"Hello"}
                }]}}]}]}
                """);
        List<ChannelMediaParser.InboundMedia> media = parser.parseMediaMessages(root);
        assertEquals(1, media.size());
        assertEquals("Hello", media.getFirst().instructionText());
        assertEquals("whatsapp", media.getFirst().channel());
    }
}
