package com.rozpost.api.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rozpost")
public class RozpostProperties {
    private String metaMode = "mock";
    private String appEnv = "local";
    private String webhookVerifyToken = "";
    private String metaAppSecret = "";
    private String tokenEncryptionKey = "";
    private String internalApiToken = "";
    /** Local/mock still used when WA Cloud media download is stubbed (META_MODE=mock). */
    private String mockMediaPath = "fixtures/sample-salon.jpg";
    private String fixturePermalink = "https://www.instagram.com/p/MOCK_ROZPOST_POSTED/";

    public String getMetaMode() { return metaMode; }
    public void setMetaMode(String metaMode) { this.metaMode = metaMode; }
    public String getAppEnv() { return appEnv; }
    public void setAppEnv(String appEnv) { this.appEnv = appEnv; }
    public String getWebhookVerifyToken() { return webhookVerifyToken; }
    public void setWebhookVerifyToken(String webhookVerifyToken) { this.webhookVerifyToken = webhookVerifyToken; }
    public String getMetaAppSecret() { return metaAppSecret; }
    public void setMetaAppSecret(String metaAppSecret) { this.metaAppSecret = metaAppSecret; }
    public String getTokenEncryptionKey() { return tokenEncryptionKey; }
    public void setTokenEncryptionKey(String tokenEncryptionKey) { this.tokenEncryptionKey = tokenEncryptionKey; }
    public String getInternalApiToken() { return internalApiToken; }
    public void setInternalApiToken(String internalApiToken) { this.internalApiToken = internalApiToken; }
    public String getMockMediaPath() { return mockMediaPath; }
    public void setMockMediaPath(String mockMediaPath) { this.mockMediaPath = mockMediaPath; }
    public String getFixturePermalink() { return fixturePermalink; }
    public void setFixturePermalink(String fixturePermalink) { this.fixturePermalink = fixturePermalink; }

    /** True unless META_MODE=live — gates mocks / simulate endpoints. */
    public boolean isMock() {
        return !"live".equalsIgnoreCase(metaMode);
    }
}
