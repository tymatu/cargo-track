package com.cargotrack.parcel.dto;

import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.warehouse.WarehouseDto;

import java.math.BigDecimal;
import java.time.Instant;

public record ParcelDto(
        Long id,
        String trackingNumber,
        ParcelStatus status,
        WarehouseDto originWarehouse,
        WarehouseDto destinationWarehouse,
        String recipientName,
        String recipientPhone,
        String recipientEmail,
        BigDecimal weightKg,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal declaredValue,
        BigDecimal price,
        Instant createdAt
) {
}
