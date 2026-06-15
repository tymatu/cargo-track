package com.cargotrack.shipment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ShipmentParcelRepository extends JpaRepository<ShipmentParcel, ShipmentParcelId> {

    boolean existsByShipmentIdAndParcelSenderId(Long shipmentId, Long senderId);

    boolean existsByParcelIdAndShipmentDriverId(Long parcelId, Long driverId);

    @Query("""
            SELECT COUNT(sp) > 0 FROM ShipmentParcel sp
            WHERE sp.shipment.truck.id = :truckId
              AND sp.parcel.sender.id = :senderId
              AND sp.shipment.status IN :statuses
            """)
    boolean existsByShipmentTruckIdAndParcelSenderIdAndShipmentStatusIn(
            @Param("truckId") Long truckId,
            @Param("senderId") Long senderId,
            @Param("statuses") java.util.Collection<ShipmentStatus> statuses);

    @Query("""
            SELECT COUNT(sp) > 0 FROM ShipmentParcel sp
            WHERE sp.parcel.trackingNumber = :trackingNumber
              AND sp.shipment.driver.id = :driverId
            """)
    boolean existsByParcelTrackingNumberAndShipmentDriverId(
            @Param("trackingNumber") String trackingNumber,
            @Param("driverId") Long driverId);

    @EntityGraph(attributePaths = {
            "shipment", "shipment.route", "shipment.truck",
            "shipment.originWarehouse", "shipment.destinationWarehouse"
    })
    Optional<ShipmentParcel> findFirstByParcelIdOrderByLoadedAtDesc(Long parcelId);
}
