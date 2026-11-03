package com.rozpost.api.interfaces.rest.v1;

import com.rozpost.api.application.simulation.WaSimulationService;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * META_MODE=mock only. Simulates inbound WA media for smoke/E2E without live Cloud API.
 * Alias kept for compatibility: POST /api/v1/simulations/wa-message
 * Also exposed as /api/demo/simulate-wa-* only via deprecated redirects? — removed; use /api/v1.
 */
@RestController
@RequestMapping("/api/v1/simulations")
@ConditionalOnProperty(name = "rozpost.meta-mode", havingValue = "mock", matchIfMissing = true)
public class SimulationV1Controller {
  private final WaSimulationService simulation;

  public SimulationV1Controller(WaSimulationService simulation) {
    this.simulation = simulation;
  }

  @PostMapping("/wa-message")
  public Map<String, Object> waMessage(@RequestBody Map<String, Object> body) {
    return simulation.simulateInboundMedia(body);
  }
}
