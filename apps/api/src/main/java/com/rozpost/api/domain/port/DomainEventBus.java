package com.rozpost.api.domain.port;

import com.rozpost.api.domain.event.DomainEvent;

public interface DomainEventBus {
    void publish(DomainEvent event);
}
