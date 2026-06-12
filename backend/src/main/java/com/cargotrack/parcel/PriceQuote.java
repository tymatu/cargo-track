package com.cargotrack.parcel;

import java.math.BigDecimal;

public record PriceQuote(
        BigDecimal price,
        BigDecimal chargeableWeightKg,
        BigDecimal distanceKm
) {
}
