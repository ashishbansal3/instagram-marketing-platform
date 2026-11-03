package com.rozpost.api.interfaces.rest;

import com.rozpost.api.application.service.OnboardingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    public record OnboardingRequest(
            String phoneE164,
            String phone,
            String businessName,
            String category,
            String city,
            String bio,
            String services,
            String address,
            String hours,
            String hoursJson,
            Double latitude,
            Double longitude,
            String locale
    ) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@RequestBody OnboardingRequest req) {
        String phone = firstNonBlank(req.phoneE164(), req.phone());
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phoneE164 required");
        }
        if (req.businessName() == null || req.businessName().isBlank()) {
            throw new IllegalArgumentException("businessName required");
        }
        String hoursJson = firstNonBlank(req.hoursJson(), req.hours());
        return onboarding.onboard(new OnboardingService.Command(
                phone,
                req.businessName(),
                req.category(),
                req.city(),
                req.locale(),
                req.bio(),
                req.services(),
                req.address(),
                hoursJson,
                req.latitude(),
                req.longitude()
        ));
    }

    @GetMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable("id") UUID id) {
        return onboarding.status(id);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
