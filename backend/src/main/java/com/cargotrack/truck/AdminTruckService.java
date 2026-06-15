package com.cargotrack.truck;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.common.ApiException;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.warehouse.Warehouse;
import com.cargotrack.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminTruckService {

    private static final EnumSet<ShipmentStatus> ACTIVE_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private final TruckRepository truckRepository;
    private final WarehouseRepository warehouseRepository;
    private final ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public List<TruckDto> findAll() {
        return truckRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Truck::getPlateNumber))
                .map(TruckDto::from)
                .toList();
    }

    @Transactional
    @Auditable(action = AuditAction.TRUCK_CREATED, entityType = "Truck")
    public TruckDto create(TruckRequest request) {
        if (request.status() == TruckStatus.IN_TRANSIT) {
            throw ApiException.badRequest(
                    "Статус IN_TRANSIT назначается автоматически при отправлении рейса");
        }
        if (truckRepository.existsByPlateNumberIgnoreCase(request.plateNumber().trim())) {
            throw ApiException.conflict("Грузовик с таким номером уже существует");
        }
        Warehouse warehouse = loadWarehouse(request.homeWarehouseId());
        Truck truck = truckRepository.save(Truck.builder()
                .plateNumber(request.plateNumber().trim().toUpperCase())
                .model(request.model())
                .capacityKg(request.capacityKg())
                .status(request.status())
                .homeWarehouse(warehouse)
                .currentLat(warehouse.getLatitude())
                .currentLng(warehouse.getLongitude())
                .build());
        return TruckDto.from(truck);
    }

    @Transactional
    @Auditable(action = AuditAction.TRUCK_UPDATED, entityType = "Truck")
    public TruckDto update(Long id, TruckRequest request) {
        if (request.status() == TruckStatus.IN_TRANSIT) {
            throw ApiException.badRequest(
                    "Статус IN_TRANSIT назначается автоматически при отправлении рейса");
        }
        Truck truck = truckRepository.findLockedById(id)
                .orElseThrow(() -> ApiException.notFound("Грузовик не найден"));
        if (truckRepository.existsByPlateNumberIgnoreCaseAndIdNot(
                request.plateNumber().trim(), id)) {
            throw ApiException.conflict("Грузовик с таким номером уже существует");
        }
        boolean active = shipmentRepository.existsByTruckIdAndStatusIn(id, ACTIVE_STATUSES);
        if (active && (request.status() != truck.getStatus()
                || !request.homeWarehouseId().equals(truck.getHomeWarehouse().getId())
                || request.capacityKg().compareTo(truck.getCapacityKg()) != 0)) {
            throw ApiException.conflict("Нельзя менять назначенный на активный рейс грузовик");
        }
        truck.setPlateNumber(request.plateNumber().trim().toUpperCase());
        truck.setModel(request.model());
        truck.setCapacityKg(request.capacityKg());
        truck.setStatus(request.status());
        truck.setHomeWarehouse(loadWarehouse(request.homeWarehouseId()));
        return TruckDto.from(truck);
    }

    @Transactional
    @Auditable(action = AuditAction.TRUCK_DELETED, entityType = "Truck")
    public TruckDto delete(Long id) {
        Truck truck = truckRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Грузовик не найден"));
        if (shipmentRepository.existsByTruckId(id)) {
            throw ApiException.conflict("Нельзя удалить грузовик с историей рейсов");
        }
        TruckDto dto = TruckDto.from(truck);
        truckRepository.delete(truck);
        return dto;
    }

    private Warehouse loadWarehouse(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Склад не найден: " + id));
    }
}
