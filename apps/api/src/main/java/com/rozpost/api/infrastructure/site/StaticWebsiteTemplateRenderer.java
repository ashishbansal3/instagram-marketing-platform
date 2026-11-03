package com.rozpost.api.infrastructure.site;

import com.rozpost.api.domain.port.WebsiteTemplateRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class StaticWebsiteTemplateRenderer implements WebsiteTemplateRenderer {
    static final String TEMPLATE_ID = "local-professional-v1";

    private final String templateHtml;

    public StaticWebsiteTemplateRenderer() {
        try {
            this.templateHtml =
                    new ClassPathResource("templates/business-site-v1.html")
                            .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing business site template", e);
        }
    }

    @Override
    public String templateId() {
        return TEMPLATE_ID;
    }

    @Override
    public String render(BusinessSiteCopy copy) {
        String bio =
                blankTo(
                        copy.bio(),
                        copy.businessName()
                                + " is a trusted "
                                + blankTo(copy.category(), "local business")
                                + " in "
                                + blankTo(copy.city(), "your city")
                                + ".");
        String servicesHtml = servicesList(copy.services(), copy.category());
        String address =
                blankTo(
                        copy.address(),
                        blankTo(copy.city(), "India") + " — exact pin coming soon");
        String hours = formatHours(copy.hoursJson());
        String phone = blankTo(copy.phoneE164(), "");
        String phoneTel = phone.replace(" ", "").replace("-", "");

        return templateHtml
                .replace("{{BUSINESS_NAME}}", esc(copy.businessName()))
                .replace("{{CITY}}", esc(blankTo(copy.city(), "")))
                .replace("{{CATEGORY}}", esc(blankTo(copy.category(), "Local business")))
                .replace("{{BIO}}", esc(bio))
                .replace("{{BIO_META}}", esc(truncate(bio, 155)))
                .replace("{{PHONE_TEL}}", esc(phoneTel))
                .replace("{{PHONE_DISPLAY}}", esc(phone.isBlank() ? "Contact us" : phone))
                .replace("{{SERVICES_LIST}}", servicesHtml)
                .replace("{{ADDRESS_LINE}}", esc(address))
                .replace("{{HOURS_LINE}}", esc(hours));
    }

    private static String servicesList(String services, String category) {
        String raw = services == null || services.isBlank() ? defaultServices(category) : services;
        return Arrays.stream(raw.split("[,\\n|;]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> "<li>" + esc(s) + "</li>")
                .collect(Collectors.joining());
    }

    private static String defaultServices(String category) {
        if (category == null) {
            return "Consultations, Walk-ins, Custom requests";
        }
        String c = category.toLowerCase();
        if (c.contains("salon") || c.contains("beauty")) {
            return "Haircut & styling, Facial & skincare, Bridal / party makeup";
        }
        if (c.contains("clinic") || c.contains("health")) {
            return "Consultations, Follow-ups, Preventive care";
        }
        if (c.contains("furniture") || c.contains("home")) {
            return "Showroom visits, Custom orders, Delivery & assembly";
        }
        if (c.contains("tuition") || c.contains("education")) {
            return "Batch classes, 1:1 tutoring, Exam prep";
        }
        if (c.contains("retail") || c.contains("shop")) {
            return "In-store shopping, Product advice, Local delivery";
        }
        return "Consultations, Walk-ins, Custom requests";
    }

    private static String formatHours(String hoursJson) {
        if (hoursJson == null || hoursJson.isBlank() || "{}".equals(hoursJson.trim())) {
            return "Mon–Sat 10:00–19:00 (confirm on WhatsApp)";
        }
        String trimmed = hoursJson.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
                    .replace("{", "")
                    .replace("}", "")
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .replace(",", " · ");
        }
        return trimmed;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "…";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
