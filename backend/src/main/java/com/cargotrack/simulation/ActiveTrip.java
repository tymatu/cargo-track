package com.cargotrack.simulation;

import com.cargotrack.routing.RouteMath;
import com.cargotrack.routing.RoutePoint;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ActiveTrip {

    private final Long shipmentId;
    private final List<RoutePoint> geometry;
    private final double totalDistanceMeters;
    private double travelledMeters;
    private Instant lastAdvancedAt;

    public ActiveTrip(Long shipmentId, List<RoutePoint> geometry, Instant startedAt) {
        if (geometry == null || geometry.size() < 2) {
            throw new IllegalArgumentException("Route geometry must contain at least two points");
        }
        this.shipmentId = shipmentId;
        this.geometry = List.copyOf(geometry);
        this.totalDistanceMeters = calculateTotalDistance(this.geometry);
        this.lastAdvancedAt = startedAt;
    }

    public Long shipmentId() {
        return shipmentId;
    }

    public synchronized TripPosition advanceTo(
            Instant now, double speedKmh, double timeScale) {
        double elapsedSeconds = Math.max(
                0.0, Duration.between(lastAdvancedAt, now).toMillis() / 1000.0);
        lastAdvancedAt = now;
        return advanceAlongPolyline(elapsedSeconds * speedKmh / 3.6 * timeScale);
    }

    public synchronized TripPosition advanceAlongPolyline(double meters) {
        travelledMeters = Math.min(
                totalDistanceMeters, travelledMeters + Math.max(0.0, meters));
        double remaining = travelledMeters;
        for (int i = 1; i < geometry.size(); i++) {
            RoutePoint from = geometry.get(i - 1);
            RoutePoint to = geometry.get(i);
            double segmentLength = RouteMath.distanceMeters(from, to);
            if (remaining <= segmentLength || i == geometry.size() - 1) {
                double ratio = segmentLength == 0.0
                        ? 1.0
                        : Math.min(1.0, remaining / segmentLength);
                RoutePoint position = new RoutePoint(
                        from.latitude() + (to.latitude() - from.latitude()) * ratio,
                        from.longitude() + (to.longitude() - from.longitude()) * ratio);
                return new TripPosition(
                        position,
                        RouteMath.bearing(from, to),
                        travelledMeters >= totalDistanceMeters);
            }
            remaining -= segmentLength;
        }
        return new TripPosition(geometry.getLast(), 0.0, true);
    }

    private double calculateTotalDistance(List<RoutePoint> points) {
        double result = 0.0;
        for (int i = 1; i < points.size(); i++) {
            result += RouteMath.distanceMeters(points.get(i - 1), points.get(i));
        }
        return result;
    }
}
