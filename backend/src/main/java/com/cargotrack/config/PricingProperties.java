package com.cargotrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Тарифы расчёта цены (SDP, раздел 7.3). */
@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(
        BigDecimal base,
        BigDecimal perKg,
        BigDecimal perKm
) {
}
