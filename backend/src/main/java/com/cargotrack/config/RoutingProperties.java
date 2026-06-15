package com.cargotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.routing")
public record RoutingProperties(
        String baseUrl,
        boolean osrmEnabled,
        Duration connectTimeout,
        Duration readTimeout,
        int fallbackPoints
) {
}
