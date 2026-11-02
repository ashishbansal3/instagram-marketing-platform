package com.rozpost.api.application.event;

import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.port.DomainEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingDomainEventBus implements DomainEventBus {
    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventBus.class);

    @Override
    public void publish(DomainEvent event) {
        log.info("domain_event type={} tenantId={} correlationId={}",
                event.type(), event.tenantId(), event.correlationId());
    }
}
