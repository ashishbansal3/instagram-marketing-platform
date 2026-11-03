package com.rozpost.api.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rozpost.api.application.onboarding.WebsiteGbpSetupService;
import com.rozpost.api.domain.event.DomainEvent;
import com.rozpost.api.domain.event.OnboardingCompleted;
import com.rozpost.api.domain.port.DomainEventBus;
import com.rozpost.api.domain.port.JobEnqueuePort;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class OnboardingServiceTest {
    private JdbcTemplate jdbc;
    private DomainEventBus events;
    private JobEnqueuePort jobs;
    private WebsiteGbpSetupService websiteGbp;
    private OnboardingService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        events = mock(DomainEventBus.class);
        jobs = mock(JobEnqueuePort.class);
        websiteGbp = mock(WebsiteGbpSetupService.class);
        service = new OnboardingService(jdbc, events, jobs, websiteGbp);

        when(jdbc.query(contains("SELECT 1 FROM clients"), any(ResultSetExtractor.class), any()))
                .thenReturn(null);
        when(jdbc.query(contains("SELECT 1 FROM tenants"), any(ResultSetExtractor.class), any()))
                .thenReturn(false);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbc.update(anyString(), any(), any(), any())).thenReturn(1);
        when(jobs.enqueue(any(), any(), eq("website_gbp_setup"), eq("website-gbp"), anyString(), anyMap()))
                .thenReturn(UUID.randomUUID());
    }

    @Test
    void onboardPublishesEventAndEnqueuesWebsiteGbpJob() {
        Map<String, Object> result =
                service.onboard(
                        new OnboardingService.Command(
                                "9876543210",
                                "Glow Studio Ludhiana",
                                "Beauty Salon",
                                "Ludhiana",
                                "en-IN",
                                "Bridal & party looks",
                                "Haircut, Facial",
                                "Model Town",
                                "Mon-Sat 10-7",
                                null,
                                null));

        assertNotNull(result.get("clientId"));
        assertEquals(result.get("clientId"), result.get("id"));
        assertEquals("profile_collected", result.get("onboardingStatus"));
        assertNotNull(result.get("setupJobId"));

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(events).publish(cap.capture());
        assertEquals("OnboardingCompleted", cap.getValue().type());
        OnboardingCompleted oc = (OnboardingCompleted) cap.getValue();
        assertEquals("Glow Studio Ludhiana", oc.businessName());
        assertEquals("+919876543210", oc.phoneE164());

        verify(jobs)
                .enqueue(
                        any(),
                        any(),
                        eq("website_gbp_setup"),
                        eq("website-gbp"),
                        anyString(),
                        anyMap());
    }

    @Test
    void normalizePhoneAddsIndiaCountryCode() {
        assertEquals("+919876543210", OnboardingService.normalizePhone("9876543210"));
        assertEquals("+919876543210", OnboardingService.normalizePhone("+91 98765-43210"));
    }
}
