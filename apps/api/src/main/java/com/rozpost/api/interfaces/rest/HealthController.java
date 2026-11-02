package com.rozpost.api.interfaces.rest;

import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final RozpostProperties props;

    public HealthController(RozpostProperties props) {
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "rozpost-api",
                "ok", true,
                "metaMode", props.getMetaMode(),
                "appEnv", props.getAppEnv()
        );
    }
}
