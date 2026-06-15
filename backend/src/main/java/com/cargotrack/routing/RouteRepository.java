package com.cargotrack.routing;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByShipmentId(Long shipmentId);

    @EntityGraph(attributePaths = {"shipment", "shipment.truck"})
    Optional<Route> findDetailedByShipmentId(Long shipmentId);

    Optional<Route> findFirstByShipmentOriginWarehouseIdAndShipmentDestinationWarehouseIdOrderByCreatedAtDesc(
            Long originWarehouseId, Long destinationWarehouseId);
}
