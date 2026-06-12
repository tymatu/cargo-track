package com.cargotrack.parcel.dto;

import com.cargotrack.parcel.ParcelStatus;

import java.time.Instant;

public record TrackingEventDto(
        ParcelStatus status,
        String description,
        String warehouseCity,
        Instant createdAt
) {
}
