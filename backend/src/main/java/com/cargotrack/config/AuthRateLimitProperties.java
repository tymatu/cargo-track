package com.cargotrack.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.auth-rate-limit")
public record AuthRateLimitProperties(
        boolean enabled,
        @Positive long capacity,
        @Positive long refillTokens,
        @NotNull Duration refillPeriod,
        @NotNull Duration bucketTtl
) {
}
