package com.cargotrack.warehouse;

import java.math.BigDecimal;

public record WarehouseDto(
        Long id,
        String name,
        String city,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
