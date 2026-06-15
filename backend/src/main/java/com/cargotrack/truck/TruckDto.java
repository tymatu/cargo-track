package com.cargotrack.truck;

import com.cargotrack.common.HasId;

import java.math.BigDecimal;

public record TruckDto(
        Long id,
        String plateNumber,
        String model,
        BigDecimal capacityKg,
        TruckStatus status,
        Long homeWarehouseId
) implements HasId {

    public static TruckDto from(Truck truck) {
        return new TruckDto(
                truck.getId(),
                truck.getPlateNumber(),
                truck.getModel(),
                truck.getCapacityKg(),
                truck.getStatus(),
                truck.getHomeWarehouse().getId());
    }
}
