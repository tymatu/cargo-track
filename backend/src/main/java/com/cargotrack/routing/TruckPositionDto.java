package com.cargotrack.routing;

import com.cargotrack.truck.Truck;

import java.math.BigDecimal;
import java.time.Instant;

public record TruckPositionDto(
        BigDecimal latitude,
        BigDecimal longitude,
        double bearing,
        Instant recordedAt
) {
    public static TruckPositionDto from(Truck truck, Route route) {
        if (truck == null || truck.getCurrentLat() == null || truck.getCurrentLng() == null) {
            return null;
        }
        return new TruckPositionDto(
                truck.getCurrentLat(),
                truck.getCurrentLng(),
                RouteMath.bearingAt(route == null ? null : route.getGeometry(),
                        truck.getCurrentLat().doubleValue(), truck.getCurrentLng().doubleValue()),
                truck.getLastPositionAt());
    }
}
