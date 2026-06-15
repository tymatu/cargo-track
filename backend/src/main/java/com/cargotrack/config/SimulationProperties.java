package com.cargotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.simulation")
public record SimulationProperties(
        boolean enabled,
        double speedKmh,
        double timeScale,
        Duration tickDelay
) {
}
