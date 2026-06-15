package com.cargotrack.shipment.dto;

import com.cargotrack.common.HasId;
import com.cargotrack.routing.RouteDto;
import com.cargotrack.routing.TruckPositionDto;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckDto;
import com.cargotrack.user.UserDto;
import com.cargotrack.warehouse.WarehouseDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ShipmentDto(
        Long id,
        ShipmentStatus status,
        TruckDto truck,
        UserDto driver,
        WarehouseDto originWarehouse,
        WarehouseDto destinationWarehouse,
        Instant plannedDepartureAt,
        Instant departedAt,
        Instant arrivedAt,
        BigDecimal loadedWeightKg,
        List<ShipmentParcelDto> parcels,
        RouteDto route,
        TruckPositionDto position,
        Instant createdAt
) implements HasId {
}
