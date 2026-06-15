package com.cargotrack.routing;

public record TrackingMapDto(
        Long shipmentId,
        Long truckId,
        RouteDto route,
        TruckPositionDto position
) {
}
