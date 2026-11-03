package com.rozpost.api.domain.port;

/** Fills a static HTML template from onboarding business fields. */
public interface WebsiteTemplateRenderer {

    record BusinessSiteCopy(
            String businessName,
            String category,
            String city,
            String phoneE164,
            String bio,
            String services,
            String address,
            String hoursJson,
            String tenantSlug
    ) {}

    String templateId();

    String render(BusinessSiteCopy copy);
}
