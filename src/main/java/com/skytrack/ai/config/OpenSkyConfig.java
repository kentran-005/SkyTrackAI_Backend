package com.skytrack.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.opensky")
public class OpenSkyConfig {
    private String baseUrl = "https://opensky-network.org/api";
    private String username;
    private String password;
    private int cacheRefreshSeconds = 60;
}
