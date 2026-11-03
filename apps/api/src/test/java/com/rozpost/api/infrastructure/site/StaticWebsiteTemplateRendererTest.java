package com.rozpost.api.infrastructure.site;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rozpost.api.domain.port.WebsiteTemplateRenderer;
import org.junit.jupiter.api.Test;

class StaticWebsiteTemplateRendererTest {
    @Test
    void rendersBusinessFieldsIntoHtml() {
        StaticWebsiteTemplateRenderer renderer = new StaticWebsiteTemplateRenderer();
        String html =
                renderer.render(
                        new WebsiteTemplateRenderer.BusinessSiteCopy(
                                "Glow Studio",
                                "Beauty Salon",
                                "Ludhiana",
                                "+919876543210",
                                "Bridal makeup specialists",
                                "Haircut, Facial",
                                "Model Town",
                                "Mon-Sat 10-7",
                                "glow-studio"));
        assertTrue(html.contains("Glow Studio"));
        assertTrue(html.contains("Ludhiana"));
        assertTrue(html.contains("Bridal makeup specialists"));
        assertTrue(html.contains("Haircut"));
        assertTrue(html.contains("tel:+919876543210"));
        assertTrue(html.contains("Model Town"));
    }
}
