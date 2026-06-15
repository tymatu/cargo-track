package com.cargotrack.routing;

import com.cargotrack.common.GeoUtils;
import com.cargotrack.config.RoutingProperties;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.warehouse.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final double FALLBACK_SPEED_KMH = 65.0;

    private final RouteRepository routeRepository;
    private final RoutingProperties properties;
    private final RestClient osrmRestClient;

    @Transactional
    public Route ensureRoute(Shipment shipment) {
        if (shipment.getRoute() != null) {
            return shipment.getRoute();
        }
        Route route = routeRepository.findByShipmentId(shipment.getId())
                .orElseGet(() -> createRoute(shipment));
        shipment.setRoute(route);
        return route;
    }

    private Route createRoute(Shipment shipment) {
        Warehouse origin = shipment.getOriginWarehouse();
        Warehouse destination = shipment.getDestinationWarehouse();
        Route cached = routeRepository
                .findFirstByShipmentOriginWarehouseIdAndShipmentDestinationWarehouseIdOrderByCreatedAtDesc(
                        origin.getId(), destination.getId())
                .filter(previous -> matchesCurrentWarehouseCoordinates(previous, origin, destination))
                .map(previous -> copyRoute(shipment, previous))
                .orElse(null);
        if (cached != null) {
            return routeRepository.save(cached);
        }

        Route route = properties.osrmEnabled() ? requestOsrm(shipment) : null;
        return routeRepository.save(route == null ? fallback(shipment) : route);
    }

    private Route requestOsrm(Shipment shipment) {
        Warehouse origin = shipment.getOriginWarehouse();
        Warehouse destination = shipment.getDestinationWarehouse();
        String coordinates = "%s,%s;%s,%s".formatted(
                origin.getLongitude(), origin.getLatitude(),
                destination.getLongitude(), destination.getLatitude());
        try {
            OsrmResponse response = osrmRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/route/v1/driving/{coordinates}")
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .build(coordinates))
                    .retrieve()
                    .body(OsrmResponse.class);
            if (response == null || !"Ok".equalsIgnoreCase(response.code())
                    || response.routes() == null || response.routes().isEmpty()) {
                return null;
            }
            OsrmRoute first = response.routes().getFirst();
            List<RoutePoint> points = first.geometry().coordinates().stream()
                    .filter(coordinate -> coordinate.size() >= 2)
                    .map(coordinate -> new RoutePoint(coordinate.get(1), coordinate.get(0)))
                    .toList();
            if (points.size() < 2) {
                return null;
            }
            return buildRoute(
                    shipment,
                    first.distance() / 1000.0,
                    Math.max(1, (int) Math.ceil(first.duration() / 60.0)),
                    points,
                    RouteSource.OSRM);
        } catch (RuntimeException exception) {
            log.warn("OSRM request failed for shipment {}, using fallback: {}",
                    shipment.getId(), exception.getMessage());
            return null;
        }
    }

    private Route fallback(Shipment shipment) {
        Warehouse origin = shipment.getOriginWarehouse();
        Warehouse destination = shipment.getDestinationWarehouse();
        int pointCount = Math.max(2, properties.fallbackPoints());
        List<RoutePoint> points = new ArrayList<>(pointCount);
        double originLat = origin.getLatitude().doubleValue();
        double originLng = origin.getLongitude().doubleValue();
        double destinationLat = destination.getLatitude().doubleValue();
        double destinationLng = destination.getLongitude().doubleValue();
        for (int i = 0; i < pointCount; i++) {
            double ratio = (double) i / (pointCount - 1);
            points.add(new RoutePoint(
                    originLat + (destinationLat - originLat) * ratio,
                    originLng + (destinationLng - originLng) * ratio));
        }
        double distanceKm = GeoUtils.haversineKm(
                originLat, originLng, destinationLat, destinationLng);
        int durationMinutes = Math.max(
                1, (int) Math.ceil(distanceKm / FALLBACK_SPEED_KMH * 60.0));
        return buildRoute(
                shipment, distanceKm, durationMinutes, points, RouteSource.FALLBACK);
    }

    private Route copyRoute(Shipment shipment, Route previous) {
        return Route.builder()
                .shipment(shipment)
                .distanceKm(previous.getDistanceKm())
                .durationMin(previous.getDurationMin())
                .originLatitude(shipment.getOriginWarehouse().getLatitude())
                .originLongitude(shipment.getOriginWarehouse().getLongitude())
                .destinationLatitude(shipment.getDestinationWarehouse().getLatitude())
                .destinationLongitude(shipment.getDestinationWarehouse().getLongitude())
                .geometry(List.copyOf(previous.getGeometry()))
                .source(RouteSource.CACHE)
                .build();
    }

    private Route buildRoute(Shipment shipment, double distanceKm, int durationMin,
                             List<RoutePoint> geometry, RouteSource source) {
        return Route.builder()
                .shipment(shipment)
                .distanceKm(BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP))
                .durationMin(durationMin)
                .originLatitude(shipment.getOriginWarehouse().getLatitude())
                .originLongitude(shipment.getOriginWarehouse().getLongitude())
                .destinationLatitude(shipment.getDestinationWarehouse().getLatitude())
                .destinationLongitude(shipment.getDestinationWarehouse().getLongitude())
                .geometry(geometry)
                .source(source)
                .build();
    }

    private boolean matchesCurrentWarehouseCoordinates(
            Route route, Warehouse origin, Warehouse destination) {
        return sameCoordinate(route.getOriginLatitude(), origin.getLatitude())
                && sameCoordinate(route.getOriginLongitude(), origin.getLongitude())
                && sameCoordinate(route.getDestinationLatitude(), destination.getLatitude())
                && sameCoordinate(route.getDestinationLongitude(), destination.getLongitude());
    }

    private boolean sameCoordinate(BigDecimal cached, BigDecimal current) {
        return cached != null && current != null && cached.compareTo(current) == 0;
    }

    private record OsrmResponse(String code, List<OsrmRoute> routes) {
    }

    private record OsrmRoute(double distance, double duration, GeoJsonGeometry geometry) {
    }

    private record GeoJsonGeometry(String type, List<List<Double>> coordinates) {
    }
}
