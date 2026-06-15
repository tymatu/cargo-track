package com.cargotrack.shipment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface ShipmentRepository
        extends JpaRepository<Shipment, Long>, JpaSpecificationExecutor<Shipment> {

    boolean existsByTruckIdAndStatusIn(Long truckId, Collection<ShipmentStatus> statuses);

    boolean existsByDriverIdAndStatusIn(Long driverId, Collection<ShipmentStatus> statuses);

    boolean existsByTruckId(Long truckId);

    boolean existsByOriginWarehouseIdOrDestinationWarehouseId(
            Long originWarehouseId, Long destinationWarehouseId);

    @Query("""
            SELECT COUNT(s) > 0 FROM Shipment s
            WHERE (s.originWarehouse.id = :warehouseId OR s.destinationWarehouse.id = :warehouseId)
              AND s.status IN :statuses
            """)
    boolean existsActiveByWarehouseId(
            @Param("warehouseId") Long warehouseId,
            @Param("statuses") Collection<ShipmentStatus> statuses);

    long countByStatus(ShipmentStatus status);

    long countByStatusAndArrivedAtBetween(
            ShipmentStatus status, Instant from, Instant to);

    boolean existsByIdAndDriverId(Long id, Long driverId);

    boolean existsByIdAndOriginWarehouseId(Long id, Long warehouseId);

    boolean existsByTruckIdAndDriverIdAndStatusIn(
            Long truckId, Long driverId, Collection<ShipmentStatus> statuses);

    boolean existsByTruckIdAndOriginWarehouseIdAndStatusIn(
            Long truckId, Long warehouseId, Collection<ShipmentStatus> statuses);

    Page<Shipment> findByDriverId(Long driverId, Pageable pageable);

    Page<Shipment> findByDriverIdAndStatus(
            Long driverId, ShipmentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"route", "truck"})
    List<Shipment> findAllByStatus(ShipmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findLockedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "truck", "truck.homeWarehouse", "driver",
            "originWarehouse", "destinationWarehouse",
            "parcelLinks", "parcelLinks.parcel", "route"
    })
    @Query("SELECT DISTINCT s FROM Shipment s WHERE s.id = :id")
    Optional<Shipment> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
            "truck", "truck.homeWarehouse", "driver",
            "originWarehouse", "destinationWarehouse",
            "parcelLinks", "parcelLinks.parcel", "route"
    })
    @Query("SELECT DISTINCT s FROM Shipment s WHERE s.id IN :ids")
    List<Shipment> findDetailedByIdIn(@Param("ids") Collection<Long> ids);
}
