package com.rozpost.api.infrastructure.meta;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMetaModeFactoryTest {

    @Test
    void mockModeReturnsFixtureQuotaWithoutHardcoding() {
        RozpostProperties props = new RozpostProperties();
        props.setMetaMode("mock");
        ObjectMapper mapper = new ObjectMapper();
        DefaultMetaModeFactory factory = new DefaultMetaModeFactory(
                props,
                new MockMetaGraphClient(mapper),
                new LiveMetaGraphClient(mapper, "v21.0", "")
        );
        assertTrue(factory.isMock());
        assertEquals("mock", factory.mode());
        Map<String, Object> limit = factory.graphClient().contentPublishingLimit("ig-1");
        assertTrue(((Number) limit.get("quota_total")).longValue() > 0);
        assertEquals("fixture", limit.get("source"));
    }

    @Test
    void liveModeSelectsLiveClient() {
        RozpostProperties props = new RozpostProperties();
        props.setMetaMode("live");
        ObjectMapper mapper = new ObjectMapper();
        DefaultMetaModeFactory factory = new DefaultMetaModeFactory(
                props,
                new MockMetaGraphClient(mapper),
                new LiveMetaGraphClient(mapper, "v21.0", "placeholder-token")
        );
        assertFalse(factory.isMock());
        assertEquals("live", factory.mode());
        assertTrue(factory.graphClient() instanceof LiveMetaGraphClient);
    }
}
