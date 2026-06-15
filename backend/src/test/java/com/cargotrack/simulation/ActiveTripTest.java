package com.cargotrack.simulation;

import com.cargotrack.routing.RouteMath;
import com.cargotrack.routing.RoutePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveTripTest {

    private static final RoutePoint START = new RoutePoint(50.075538, 14.437800);
    private static final RoutePoint MIDDLE = new RoutePoint(50.080000, 14.500000);
    private static final RoutePoint END = new RoutePoint(50.100000, 14.600000);

    @Test
    void advanceAlongPolyline_interpolatesAcrossSegments() {
        ActiveTrip trip = new ActiveTrip(1L, List.of(START, MIDDLE, END), Instant.EPOCH);
        double firstSegment = RouteMath.distanceMeters(START, MIDDLE);

        TripPosition position = trip.advanceAlongPolyline(firstSegment + 10.0);

        assertThat(position.point().latitude()).isGreaterThan(MIDDLE.latitude());
        assertThat(position.point().longitude()).isGreaterThan(MIDDLE.longitude());
        assertThat(position.completed()).isFalse();
    }

    @Test
    void advanceAlongPolyline_stopsExactlyAtDestination() {
        ActiveTrip trip = new ActiveTrip(1L, List.of(START, MIDDLE, END), Instant.EPOCH);

        TripPosition position = trip.advanceAlongPolyline(Double.MAX_VALUE);

        assertThat(position.point()).isEqualTo(END);
        assertThat(position.completed()).isTrue();
    }
}
