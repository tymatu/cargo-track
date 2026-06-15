package com.cargotrack.parcel.dto;

import com.cargotrack.routing.TrackingMapDto;

import java.util.List;

public record ParcelDetailDto(
        ParcelDto parcel,
        List<TrackingEventDto> events,
        TrackingMapDto tracking
) {
}
