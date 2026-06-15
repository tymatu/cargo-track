package com.cargotrack.simulation;

import com.cargotrack.routing.RoutePoint;

public record TripPosition(
        RoutePoint point,
        double bearing,
        boolean completed
) {
}
