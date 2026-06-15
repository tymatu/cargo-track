package com.cargotrack.truck;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, Long> {

    boolean existsByPlateNumberIgnoreCase(String plateNumber);

    boolean existsByPlateNumberIgnoreCaseAndIdNot(String plateNumber, Long id);

    boolean existsByHomeWarehouseId(Long warehouseId);

    long countByStatus(TruckStatus status);

    @Override
    @EntityGraph(attributePaths = {"homeWarehouse"})
    List<Truck> findAll();

    List<Truck> findByHomeWarehouseIdAndStatusOrderByPlateNumber(
            Long warehouseId, TruckStatus status);

    List<Truck> findByStatusOrderByPlateNumber(TruckStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Truck t WHERE t.id = :id")
    Optional<Truck> findLockedById(@Param("id") Long id);
}
