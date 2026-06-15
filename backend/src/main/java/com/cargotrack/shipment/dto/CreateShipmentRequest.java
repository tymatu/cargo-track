package com.cargotrack.shipment.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public record CreateShipmentRequest(
        @NotNull @Positive Long truckId,
        @NotNull @Positive Long driverId,
        @NotNull @Positive Long destinationWarehouseId,
        @FutureOrPresent Instant plannedDepartureAt
) {
}
