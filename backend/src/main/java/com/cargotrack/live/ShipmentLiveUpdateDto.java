package com.cargotrack.live;

import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckStatus;

import java.time.Instant;

public record ShipmentLiveUpdateDto(
        Long shipmentId,
        ShipmentStatus status,
        TruckStatus truckStatus,
        TruckPositionDto position,
        Instant arrivedAt
) {
}
