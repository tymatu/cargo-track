package com.cargotrack.live;

import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.dto.TrackingEventDto;

public record ParcelLiveUpdateDto(
        Long parcelId,
        ParcelStatus status,
        TrackingEventDto event
) {
}
