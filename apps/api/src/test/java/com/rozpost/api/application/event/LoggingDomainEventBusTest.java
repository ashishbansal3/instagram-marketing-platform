package com.rozpost.api.application.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rozpost.api.domain.event.MediaProcessed;
import com.rozpost.api.domain.event.OnboardingCompleted;
import com.rozpost.api.domain.event.ReminderSent;
import com.rozpost.api.domain.port.DomainEventSubscriber;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoggingDomainEventBusTest {

    @Mock JdbcTemplate jdbc;
    @Mock StringRedisTemplate redis;
    @Mock StreamOperations<String, Object, Object> streamOps;

    @Test
    @SuppressWarnings("unchecked")
    void persistsAndNotifiesSubscribersWithPostAggregate() {
        AtomicReference<String> seen = new AtomicReference<>();
        DomainEventSubscriber sub = new DomainEventSubscriber() {
            @Override
            public String eventType() {
                return "MediaProcessed";
            }

            @Override
            public void onEvent(com.rozpost.api.domain.event.DomainEvent event) {
                seen.set(event.type());
            }
        };
        when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LoggingDomainEventBus bus =
                new LoggingDomainEventBus(jdbc, redis, mapper, List.of(sub));
        UUID tenant = UUID.randomUUID();
        UUID post = UUID.randomUUID();
        bus.publish(new MediaProcessed(tenant, "corr-1", post, "asset-1", Instant.now()));

        assertEquals("MediaProcessed", seen.get());
        ArgumentCaptor<Object> agg = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO domain_events"), any(), any(), any(), agg.capture(), any(), any());
        assertEquals(post.toString(), agg.getValue().toString());
        verify(streamOps).add(anyString(), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reminderSentUsesPostAggregateType() {
        when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LoggingDomainEventBus bus =
                new LoggingDomainEventBus(jdbc, redis, mapper, List.of());
        UUID post = UUID.randomUUID();
        bus.publish(new ReminderSent(UUID.randomUUID(), "c", post, "whatsapp", Instant.now()));
        ArgumentCaptor<Object> type = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO domain_events"), any(), any(), type.capture(), any(), any(), any());
        assertEquals("post", type.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onboardingCompletedUsesClientAggregate() {
        when(redis.opsForStream()).thenReturn((StreamOperations) streamOps);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LoggingDomainEventBus bus =
                new LoggingDomainEventBus(jdbc, redis, mapper, List.of());
        UUID tenant = UUID.randomUUID();
        UUID client = UUID.randomUUID();
        bus.publish(
                new OnboardingCompleted(
                        tenant,
                        client,
                        "glow-studio",
                        "Glow Studio",
                        "Beauty Salon",
                        "Ludhiana",
                        "+919876543210",
                        "bio",
                        "Haircut",
                        "Model Town",
                        null,
                        null,
                        null,
                        "corr",
                        Instant.now()));
        ArgumentCaptor<Object> type = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> agg = ArgumentCaptor.forClass(Object.class);
        verify(jdbc)
                .update(
                        contains("INSERT INTO domain_events"),
                        any(),
                        any(),
                        type.capture(),
                        agg.capture(),
                        any(),
                        any());
        assertEquals("client", type.getValue());
        assertEquals(client.toString(), agg.getValue().toString());
    }
}
