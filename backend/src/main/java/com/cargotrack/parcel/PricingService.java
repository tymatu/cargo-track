package com.cargotrack.parcel;

import com.cargotrack.config.PricingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Цена = base + perKg * max(вес, объёмный вес) + perKm * расстояние.
 * Объёмный вес = Д*Ш*В / 5000 (см → кг) — индустриальный делитель (SDP, раздел 7.3).
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final BigDecimal VOLUMETRIC_DIVISOR = BigDecimal.valueOf(5000);

    private final PricingProperties properties;

    public PriceQuote quote(BigDecimal weightKg,
                            BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                            double distanceKm) {
        BigDecimal chargeable = weightKg.max(volumetricWeight(lengthCm, widthCm, heightCm));
        BigDecimal distance = BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP);

        BigDecimal price = properties.base()
                .add(properties.perKg().multiply(chargeable))
                .add(properties.perKm().multiply(distance))
                .setScale(2, RoundingMode.HALF_UP);

        return new PriceQuote(price, chargeable.setScale(2, RoundingMode.HALF_UP), distance);
    }

    private BigDecimal volumetricWeight(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
        if (lengthCm == null || widthCm == null || heightCm == null) {
            return BigDecimal.ZERO;
        }
        return lengthCm.multiply(widthCm).multiply(heightCm)
                .divide(VOLUMETRIC_DIVISOR, 2, RoundingMode.HALF_UP);
    }
}
