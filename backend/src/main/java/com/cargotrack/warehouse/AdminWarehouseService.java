package com.cargotrack.warehouse;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.common.ApiException;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminWarehouseService {

    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private final WarehouseRepository warehouseRepository;
    private final WarehouseMapper warehouseMapper;
    private final TruckRepository truckRepository;
    private final UserRepository userRepository;
    private final ParcelRepository parcelRepository;
    private final ShipmentRepository shipmentRepository;

    @Transactional(readOnly = true)
    public List<WarehouseDto> findAll() {
        return warehouseMapper.toDtos(warehouseRepository.findAll(Sort.by("city", "name")));
    }

    @Transactional
    @Auditable(action = AuditAction.WAREHOUSE_CREATED, entityType = "Warehouse")
    public WarehouseDto create(WarehouseRequest request) {
        if (warehouseRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw ApiException.conflict("Склад с таким названием уже существует");
        }
        return warehouseMapper.toDto(warehouseRepository.save(toEntity(request)));
    }

    @Transactional
    @Auditable(action = AuditAction.WAREHOUSE_UPDATED, entityType = "Warehouse")
    public WarehouseDto update(Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Склад не найден"));
        if (warehouseRepository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw ApiException.conflict("Склад с таким названием уже существует");
        }
        if (coordinatesChanged(warehouse, request)
                && shipmentRepository.existsActiveByWarehouseId(id, ACTIVE_SHIPMENT_STATUSES)) {
            throw ApiException.conflict(
                    "Warehouse coordinates cannot be changed while active shipments use it");
        }
        warehouse.setName(request.name().trim());
        warehouse.setCity(request.city().trim());
        warehouse.setAddress(request.address().trim());
        warehouse.setLatitude(request.latitude());
        warehouse.setLongitude(request.longitude());
        return warehouseMapper.toDto(warehouse);
    }

    @Transactional
    @Auditable(action = AuditAction.WAREHOUSE_DELETED, entityType = "Warehouse")
    public WarehouseDto delete(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Склад не найден"));
        if (truckRepository.existsByHomeWarehouseId(id)
                || userRepository.existsByWarehouseId(id)
                || parcelRepository.existsByOriginWarehouseIdOrDestinationWarehouseId(id, id)
                || shipmentRepository.existsByOriginWarehouseIdOrDestinationWarehouseId(id, id)) {
            throw ApiException.conflict("Нельзя удалить используемый склад");
        }
        WarehouseDto dto = warehouseMapper.toDto(warehouse);
        warehouseRepository.delete(warehouse);
        return dto;
    }

    private Warehouse toEntity(WarehouseRequest request) {
        return Warehouse.builder()
                .name(request.name().trim())
                .city(request.city().trim())
                .address(request.address().trim())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    private boolean coordinatesChanged(Warehouse warehouse, WarehouseRequest request) {
        return different(warehouse.getLatitude(), request.latitude())
                || different(warehouse.getLongitude(), request.longitude());
    }

    private boolean different(BigDecimal current, BigDecimal requested) {
        return current == null || requested == null || current.compareTo(requested) != 0;
    }
}
