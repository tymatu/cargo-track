package com.cargotrack.parcel.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateParcelRequest(
        @NotNull Long originWarehouseId,
        @NotNull Long destinationWarehouseId,
        @NotBlank @Size(max = 200) String recipientName,
        @NotBlank @Size(max = 30) String recipientPhone,
        @Email @Size(max = 255) String recipientEmail,
        @NotNull @Positive @DecimalMax("10000") BigDecimal weightKg,
        @Positive @DecimalMax("500") BigDecimal lengthCm,
        @Positive @DecimalMax("500") BigDecimal widthCm,
        @Positive @DecimalMax("500") BigDecimal heightCm,
        @PositiveOrZero BigDecimal declaredValue
) {
}
