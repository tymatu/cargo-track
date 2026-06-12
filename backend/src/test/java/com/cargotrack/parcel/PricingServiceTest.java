package com.cargotrack.parcel;

import com.cargotrack.config.PricingProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    // base=5.00, perKg=0.50, perKm=0.05
    private final PricingService service = new PricingService(new PricingProperties(
            new BigDecimal("5.00"), new BigDecimal("0.50"), new BigDecimal("0.05")));

    @Test
    void usesActualWeightWhenHeavierThanVolumetric() {
        // объёмный: 30*20*10/5000 = 1.2 кг < 10 кг
        PriceQuote quote = service.quote(new BigDecimal("10"),
                new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"), 100.0);

        assertThat(quote.chargeableWeightKg()).isEqualByComparingTo("10");
        // 5.00 + 0.50*10 + 0.05*100 = 15.00
        assertThat(quote.price()).isEqualByComparingTo("15.00");
    }

    @Test
    void usesVolumetricWeightWhenBulky() {
        // объёмный: 100*100*100/5000 = 200 кг > 5 кг
        PriceQuote quote = service.quote(new BigDecimal("5"),
                new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100"), 0.0);

        assertThat(quote.chargeableWeightKg()).isEqualByComparingTo("200");
        // 5.00 + 0.50*200 + 0 = 105.00
        assertThat(quote.price()).isEqualByComparingTo("105.00");
    }

    @Test
    void missingDimensionsFallBackToActualWeight() {
        PriceQuote quote = service.quote(new BigDecimal("2"), null, null, null, 10.0);

        assertThat(quote.chargeableWeightKg()).isEqualByComparingTo("2");
        // 5.00 + 0.50*2 + 0.05*10 = 6.50
        assertThat(quote.price()).isEqualByComparingTo("6.50");
    }

    @Test
    void priceIsRoundedToTwoDecimals() {
        PriceQuote quote = service.quote(new BigDecimal("1.333"), null, null, null, 33.333);

        assertThat(quote.price().scale()).isEqualTo(2);
        // расстояние округляется до 33.3; 5.00 + 0.6665 + 1.665 = 7.3315 → 7.33
        assertThat(quote.price()).isEqualByComparingTo("7.33");
    }
}
