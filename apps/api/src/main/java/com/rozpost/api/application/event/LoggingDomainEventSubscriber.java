package com.rozpost.api.application.event;

import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.port.DomainEventSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default subscriber — structured log only (analytics/billing plug in later without touching producers). */
@Component
public class LoggingDomainEventSubscriber implements DomainEventSubscriber {
    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventSubscriber.class);

    @Override
    public String eventType() {
        return "*";
    }

    @Override
    public void onEvent(DomainEvent event) {
        log.info(
                "domain_event_subscriber type={} tenantId={} correlationId={}",
                event.type(),
                event.tenantId(),
                event.correlationId());
    }
}
