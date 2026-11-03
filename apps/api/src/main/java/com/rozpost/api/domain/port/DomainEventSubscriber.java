package com.rozpost.api.domain.port;

import com.rozpost.api.domain.event.DomainEvent;

/** Open/Closed subscriber for domain events (notification, analytics, billing). */
public interface DomainEventSubscriber {
    /** Event type this subscriber handles, or {@code *} for all. */
    String eventType();

    void onEvent(DomainEvent event);
}
