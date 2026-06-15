package com.cargotrack.shipment.dto;

import com.cargotrack.parcel.ParcelStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ShipmentParcelDto(
        Long id,
        String trackingNumber,
        ParcelStatus status,
        BigDecimal weightKg,
        Instant loadedAt
) {
}
