package com.cargotrack.parcel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PriceRequest(
        @NotNull Long originWarehouseId,
        @NotNull Long destinationWarehouseId,
        @NotNull @Positive @DecimalMax("10000") BigDecimal weightKg,
        @Positive @DecimalMax("500") BigDecimal lengthCm,
        @Positive @DecimalMax("500") BigDecimal widthCm,
        @Positive @DecimalMax("500") BigDecimal heightCm
) {
}
