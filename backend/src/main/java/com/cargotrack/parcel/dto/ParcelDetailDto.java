package com.cargotrack.parcel.dto;

import java.util.List;

public record ParcelDetailDto(
        ParcelDto parcel,
        List<TrackingEventDto> events
) {
}
