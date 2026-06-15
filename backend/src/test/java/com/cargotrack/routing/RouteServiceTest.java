package com.cargotrack.routing;

import com.cargotrack.config.RoutingProperties;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.warehouse.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteServiceTest {

    private RouteRepository routeRepository;
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        routeRepository = mock(RouteRepository.class);
        routeService = new RouteService(
                routeRepository,
                new RoutingProperties("http://localhost", false, Duration.ofSeconds(1),
                        Duration.ofSeconds(1), 2),
                mock(RestClient.class));
        when(routeRepository.save(any(Route.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ensureRoute_reusesCachedRouteOnlyWhenCoordinateSnapshotsMatch() {
        Shipment shipment = shipment("51.000000", "15.000000", "52.000000", "16.000000");
        when(routeRepository.findByShipmentId(shipment.getId())).thenReturn(Optional.empty());
        when(routeRepository
                .findFirstByShipmentOriginWarehouseIdAndShipmentDestinationWarehouseIdOrderByCreatedAtDesc(
                        1L, 2L))
                .thenReturn(Optional.of(route("50.000000", "14.000000", "52.000000", "16.000000")));

        Route route = routeService.ensureRoute(shipment);

        assertThat(route.getSource()).isEqualTo(RouteSource.FALLBACK);
        assertThat(route.getOriginLatitude()).isEqualByComparingTo("51.000000");
        assertThat(route.getOriginLongitude()).isEqualByComparingTo("15.000000");
    }

    @Test
    void ensureRoute_reusesCachedRouteWhenCoordinateSnapshotsMatch() {
        Shipment shipment = shipment("50.000000", "14.000000", "52.000000", "16.000000");
        when(routeRepository.findByShipmentId(shipment.getId())).thenReturn(Optional.empty());
        when(routeRepository
                .findFirstByShipmentOriginWarehouseIdAndShipmentDestinationWarehouseIdOrderByCreatedAtDesc(
                        1L, 2L))
                .thenReturn(Optional.of(route("50.000000", "14.000000", "52.000000", "16.000000")));

        Route route = routeService.ensureRoute(shipment);

        assertThat(route.getSource()).isEqualTo(RouteSource.CACHE);
        assertThat(route.getGeometry()).hasSize(2);
    }

    private Shipment shipment(String originLat, String originLng, String destinationLat,
                              String destinationLng) {
        return Shipment.builder()
                .id(10L)
                .originWarehouse(warehouse(1L, originLat, originLng))
                .destinationWarehouse(warehouse(2L, destinationLat, destinationLng))
                .build();
    }

    private Warehouse warehouse(Long id, String latitude, String longitude) {
        return Warehouse.builder()
                .id(id)
                .name("Warehouse " + id)
                .city("City " + id)
                .address("Address " + id)
                .latitude(new BigDecimal(latitude))
                .longitude(new BigDecimal(longitude))
                .build();
    }

    private Route route(String originLat, String originLng, String destinationLat,
                        String destinationLng) {
        return Route.builder()
                .distanceKm(new BigDecimal("100.00"))
                .durationMin(90)
                .originLatitude(new BigDecimal(originLat))
                .originLongitude(new BigDecimal(originLng))
                .destinationLatitude(new BigDecimal(destinationLat))
                .destinationLongitude(new BigDecimal(destinationLng))
                .geometry(List.of(
                        new RoutePoint(50.0, 14.0),
                        new RoutePoint(52.0, 16.0)))
                .source(RouteSource.FALLBACK)
                .build();
    }
}
