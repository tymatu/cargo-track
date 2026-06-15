package com.cargotrack.routing;

import com.cargotrack.common.GeoUtils;

import java.util.List;

public final class RouteMath {

    private RouteMath() {
    }

    public static double distanceMeters(RoutePoint first, RoutePoint second) {
        return GeoUtils.haversineKm(
                first.latitude(), first.longitude(),
                second.latitude(), second.longitude()) * 1000.0;
    }

    public static double bearing(RoutePoint first, RoutePoint second) {
        double lat1 = Math.toRadians(first.latitude());
        double lat2 = Math.toRadians(second.latitude());
        double deltaLongitude = Math.toRadians(second.longitude() - first.longitude());
        double y = Math.sin(deltaLongitude) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(deltaLongitude);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    public static double bearingAt(List<RoutePoint> geometry, double latitude, double longitude) {
        if (geometry == null || geometry.size() < 2) {
            return 0.0;
        }
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        RoutePoint position = new RoutePoint(latitude, longitude);
        for (int i = 0; i < geometry.size(); i++) {
            double distance = distanceMeters(position, geometry.get(i));
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        int next = Math.min(nearest + 1, geometry.size() - 1);
        int previous = Math.max(0, next - 1);
        return bearing(geometry.get(previous), geometry.get(next));
    }

    public static int progressPercent(List<RoutePoint> geometry, double latitude, double longitude) {
        if (geometry == null || geometry.size() < 2) {
            return 50;
        }
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        RoutePoint position = new RoutePoint(latitude, longitude);
        for (int i = 0; i < geometry.size(); i++) {
            double distance = distanceMeters(position, geometry.get(i));
            if (distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        double progress = (nearest * 100.0) / (geometry.size() - 1);
        return Math.max(0, Math.min(100, (int) Math.round(progress)));
    }
}
