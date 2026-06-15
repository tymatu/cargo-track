package com.cargotrack.live;

import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckStatus;

public record FleetPositionDto(
        Long shipmentId,
        Long truckId,
        String plateNumber,
        TruckStatus truckStatus,
        ShipmentStatus shipmentStatus,
        TruckPositionDto position
) {
}
