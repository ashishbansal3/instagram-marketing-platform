package com.rozpost.api.infrastructure.meta;

import com.rozpost.api.domain.port.MetaGraphClient;
import com.rozpost.api.domain.port.MetaModeFactory;
import com.rozpost.api.infrastructure.config.RozpostProperties;
import org.springframework.stereotype.Component;

@Component
public class DefaultMetaModeFactory implements MetaModeFactory {

    private final RozpostProperties props;
    private final MockMetaGraphClient mock;
    private final LiveMetaGraphClient live;

    public DefaultMetaModeFactory(
            RozpostProperties props,
            MockMetaGraphClient mock,
            LiveMetaGraphClient live
    ) {
        this.props = props;
        this.mock = mock;
        this.live = live;
    }

    @Override
    public MetaGraphClient graphClient() {
        return isMock() ? mock : live;
    }

    @Override
    public boolean isMock() {
        return !"live".equalsIgnoreCase(props.getMetaMode());
    }

    @Override
    public String mode() {
        return isMock() ? "mock" : "live";
    }
}
