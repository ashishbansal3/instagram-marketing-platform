package com.rozpost.api.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.application.approval.ApprovalCommandService;
import com.rozpost.api.application.port.PostRepositoryPort;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppIngestServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock StringRedisTemplate redis;
    @Mock StreamOperations<String, Object, Object> streamOps;
    @Mock DomainEventBus eventBus;
    @Mock ApprovalCommandService approval;
    @Mock PostRepositoryPort posts;

    RozpostProperties props = new RozpostProperties();
    ObjectMapper mapper = new ObjectMapper();
    WhatsAppIngestService service;

    UUID tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID clientId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        props.setMetaMode("mock");
        service = new WhatsAppIngestService(mapper, jdbc, redis, eventBus, props, approval, posts);
    }

    @Test
    void duplicateWebhookReceiptReturnsDuplicateWithoutEnqueue() throws Exception {
        JsonNode root = mapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"919876543210","id":"wamid.DUP","type":"image",
                  "image":{"id":"m1","mime_type":"image/jpeg","caption":"x"}
                }]}}]}]}
                """);

        when(jdbc.query(contains("FROM clients"), any(ResultSetExtractor.class), any(), any(), any()))
                .thenReturn(new WhatsAppIngestService.ClientRef(clientId, tenantId, "919876543210", "active"));
        when(jdbc.update(contains("webhook_receipts"), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("dup"));

        Map<String, Object> result = service.ingest(root);
        assertEquals("duplicate", result.get("status"));
        verify(redis, never()).opsForStream();
        verify(eventBus, never()).publish(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mediaIngestPersistsAndEnqueuesOnce() throws Exception {
        JsonNode root = mapper.readTree("""
                {"entry":[{"changes":[{"value":{"messages":[{
                  "from":"919876543210","id":"wamid.NEW","type":"image",
                  "image":{"id":"m1","mime_type":"image/jpeg","caption":"Soft beige"}
                }]}}]}]}
                """);

        when(jdbc.query(contains("FROM clients"), any(ResultSetExtractor.class), any(), any(), any()))
                .thenReturn(new WhatsAppIngestService.ClientRef(clientId, tenantId, "919876543210", "active"));
        when(jdbc.update(contains("webhook_receipts"), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.query(contains("media_assets"), any(ResultSetExtractor.class), eq("wamid.NEW")))
                .thenReturn(null);
        when(jdbc.update(contains("INSERT INTO media_assets"), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbc.update(contains("INSERT INTO jobs"), any(), any(), any(), any(), any())).thenReturn(1);
        when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
        when(streamOps.add(anyString(), any(Map.class))).thenReturn(null);

        Map<String, Object> result = service.ingest(root);
        assertEquals("queued_media_download", result.get("status"));
        assertEquals("wamid.NEW", result.get("wamid"));
        verify(streamOps).add(eq("media-download"), any(Map.class));
        verify(eventBus, atLeastOnce()).publish(any());
    }
}
