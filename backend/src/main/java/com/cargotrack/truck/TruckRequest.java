package com.cargotrack.truck;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TruckRequest(
        @NotBlank @Size(max = 20) String plateNumber,
        @Size(max = 100) String model,
        @NotNull @DecimalMin("0.01") BigDecimal capacityKg,
        @NotNull TruckStatus status,
        @NotNull Long homeWarehouseId
) {
}
