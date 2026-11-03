package com.rozpost.api.infrastructure.gbp;

import com.rozpost.api.domain.port.GbpClient;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultGbpModeFactory {
    private final RozpostProperties props;
    private final MockGbpClient mock;
    private final LiveGbpClient live;

    public DefaultGbpModeFactory(RozpostProperties props, MockGbpClient mock, LiveGbpClient live) {
        this.props = props;
        this.mock = mock;
        this.live = live;
    }

    public GbpClient client() {
        return props.isMock() ? mock : live;
    }

    public boolean isMock() {
        return props.isMock();
    }

    public String mode() {
        return isMock() ? "mock" : "live";
    }
}
