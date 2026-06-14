package com.skytrack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.opensky")
public class OpenSkyConfig {
    private String baseUrl = "https://opensky-network.org/api";
    private String tokenUrl = "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";
    private String clientId;
    private String clientSecret;
    private int cacheRefreshSeconds = 90;
    private int anonymousCacheRefreshSeconds = 660;
}
