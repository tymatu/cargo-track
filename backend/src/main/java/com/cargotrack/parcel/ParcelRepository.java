package com.cargotrack.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParcelRepository
        extends JpaRepository<Parcel, Long>, JpaSpecificationExecutor<Parcel> {

    boolean existsByIdAndSenderId(Long id, Long senderId);

    @Query("""
            SELECT COUNT(p) > 0 FROM Parcel p
            WHERE p.id = :id
              AND (p.originWarehouse.id = :warehouseId
                   OR p.destinationWarehouse.id = :warehouseId)
            """)
    boolean existsByIdAtWarehouse(
            @Param("id") Long id, @Param("warehouseId") Long warehouseId);

    boolean existsByTrackingNumber(String trackingNumber);

    boolean existsByOriginWarehouseIdOrDestinationWarehouseId(
            Long originWarehouseId, Long destinationWarehouseId);

    long countByStatus(ParcelStatus status);

    @Query("""
            SELECT p.status AS status, COUNT(p) AS total
            FROM Parcel p
            GROUP BY p.status
            """)
    List<ParcelStatusCount> countGroupedByStatus();

    @Query("""
            SELECT COALESCE(SUM(p.price), 0)
            FROM Parcel p
            WHERE p.createdAt >= :from AND p.createdAt < :to
              AND p.status <> com.cargotrack.parcel.ParcelStatus.CANCELLED
            """)
    BigDecimal sumRevenueCreatedBetween(
            @Param("from") Instant from, @Param("to") Instant to);

    Optional<Parcel> findByTrackingNumber(String trackingNumber);

    boolean existsByTrackingNumberAndSenderId(String trackingNumber, Long senderId);

    @Query("""
            SELECT COUNT(p) > 0 FROM Parcel p
            WHERE p.trackingNumber = :trackingNumber
              AND (p.originWarehouse.id = :warehouseId
                   OR p.destinationWarehouse.id = :warehouseId)
            """)
    boolean existsByTrackingNumberAtWarehouse(
            @Param("trackingNumber") String trackingNumber,
            @Param("warehouseId") Long warehouseId);

    @Override
    @EntityGraph(attributePaths = {"originWarehouse", "destinationWarehouse"})
    Page<Parcel> findAll(Specification<Parcel> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"originWarehouse", "destinationWarehouse"})
    Page<Parcel> findBySenderId(Long senderId, Pageable pageable);

    @EntityGraph(attributePaths = {"originWarehouse", "destinationWarehouse"})
    Page<Parcel> findBySenderIdAndStatus(Long senderId, ParcelStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.id = :id")
    Optional<Parcel> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Parcel p WHERE p.id IN :ids")
    List<Parcel> findAllLockedByIdIn(@Param("ids") Collection<Long> ids);
}
