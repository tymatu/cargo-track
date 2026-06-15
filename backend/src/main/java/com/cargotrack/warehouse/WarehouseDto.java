package com.cargotrack.warehouse;

import com.cargotrack.common.HasId;

import java.math.BigDecimal;

public record WarehouseDto(
        Long id,
        String name,
        String city,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) implements HasId {
}
