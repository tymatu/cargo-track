package com.cargotrack.routing;

import java.math.BigDecimal;
import java.util.List;

public record RouteDto(
        BigDecimal distanceKm,
        Integer durationMin,
        List<RoutePoint> geometry,
        RouteSource source
) {
    public static RouteDto from(Route route) {
        if (route == null) {
            return null;
        }
        return new RouteDto(
                route.getDistanceKm(),
                route.getDurationMin(),
                List.copyOf(route.getGeometry()),
                route.getSource());
    }
}
