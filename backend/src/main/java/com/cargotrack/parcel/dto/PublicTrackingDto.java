package com.cargotrack.parcel.dto;

import com.cargotrack.parcel.ParcelStatus;

import java.time.Instant;
import java.util.List;

/** Публичный трекинг: без личных данных, получатель замаскирован (SDP, раздел 7.2). */
public record PublicTrackingDto(
        String trackingNumber,
        ParcelStatus status,
        String originCity,
        String destinationCity,
        String recipientNameMasked,
        Instant createdAt,
        List<TrackingEventDto> events
) {
}
