package com.cargotrack.shipment;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.IllegalStateTransitionException;
import com.cargotrack.common.PageResponse;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelMapper;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.ParcelService;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.dto.ParcelDto;
import com.cargotrack.routing.RouteService;
import com.cargotrack.shipment.dto.CreateShipmentRequest;
import com.cargotrack.shipment.dto.LoadParcelsRequest;
import com.cargotrack.shipment.dto.ShipmentDto;
import com.cargotrack.truck.Truck;
import com.cargotrack.truck.TruckDto;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.truck.TruckStatus;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserDto;
import com.cargotrack.user.UserMapper;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import com.cargotrack.warehouse.Warehouse;
import com.cargotrack.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DispatcherService {

    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    private final ParcelRepository parcelRepository;
    private final ParcelService parcelService;
    private final ParcelMapper parcelMapper;
    private final ShipmentRepository shipmentRepository;
    private final TruckRepository truckRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final UserMapper userMapper;
    private final ShipmentMapper shipmentMapper;
    private final RouteService routeService;

    @Transactional(readOnly = true)
    public PageResponse<ParcelDto> findParcels(UserPrincipal principal, ParcelStatus status,
                                               Long requestedOriginWarehouseId,
                                               Long destinationWarehouseId,
                                               Pageable pageable) {
        Specification<Parcel> specification = (root, query, cb) -> cb.conjunction();
        if (!isAdmin(principal) || requestedOriginWarehouseId != null) {
            Long warehouseId = resolveWarehouseId(principal, requestedOriginWarehouseId);
            specification = specification.and(
                    (root, query, cb) -> cb.equal(
                            root.get("originWarehouse").get("id"), warehouseId));
        }
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (destinationWarehouseId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(
                            root.get("destinationWarehouse").get("id"), destinationWarehouseId));
        }
        return PageResponse.of(parcelRepository.findAll(specification, pageable).map(parcelMapper::toDto));
    }

    @Transactional
    @Auditable(action = AuditAction.PARCEL_STATUS_CHANGED, entityType = "Parcel")
    public ParcelDto acceptParcel(Long parcelId, UserPrincipal principal) {
        Parcel parcel = loadLockedParcel(parcelId);
        requireWarehouseForActor(parcel.getOriginWarehouse().getId(), principal);
        parcelService.changeStatus(
                parcel,
                ParcelStatus.ACCEPTED_AT_ORIGIN,
                "Посылка принята на складе " + parcel.getOriginWarehouse().getName(),
                parcel.getOriginWarehouse(),
                principal.getId());
        return parcelMapper.toDto(parcel);
    }

    @Transactional
    @Auditable(action = AuditAction.PARCEL_STATUS_CHANGED, entityType = "Parcel")
    public ParcelDto deliverParcel(Long parcelId, UserPrincipal principal) {
        Parcel parcel = loadLockedParcel(parcelId);
        requireWarehouseForActor(parcel.getDestinationWarehouse().getId(), principal);
        parcelService.changeStatus(
                parcel,
                ParcelStatus.DELIVERED,
                "Посылка выдана получателю",
                parcel.getDestinationWarehouse(),
                principal.getId());
        return parcelMapper.toDto(parcel);
    }

    @Transactional(readOnly = true)
    public List<TruckDto> findAvailableTrucks(UserPrincipal principal) {
        List<Truck> trucks = isAdmin(principal)
                ? truckRepository.findByStatusOrderByPlateNumber(TruckStatus.IDLE)
                : truckRepository.findByHomeWarehouseIdAndStatusOrderByPlateNumber(
                        resolveWarehouseId(principal, null), TruckStatus.IDLE);
        return trucks
                .stream()
                .filter(truck -> !shipmentRepository.existsByTruckIdAndStatusIn(
                        truck.getId(), ACTIVE_SHIPMENT_STATUSES))
                .map(TruckDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDto> findAvailableDrivers(UserPrincipal principal) {
        List<User> drivers = isAdmin(principal)
                ? userRepository.findByRoleAndStatusOrderByLastNameAscFirstNameAsc(
                        Role.DRIVER, UserStatus.ACTIVE)
                : userRepository.findByRoleAndWarehouseIdAndStatusOrderByLastNameAscFirstNameAsc(
                        Role.DRIVER, resolveWarehouseId(principal, null), UserStatus.ACTIVE);
        return drivers
                .stream()
                .filter(driver -> !shipmentRepository.existsByDriverIdAndStatusIn(
                        driver.getId(), ACTIVE_SHIPMENT_STATUSES))
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional
    @Auditable(action = AuditAction.SHIPMENT_CREATED, entityType = "Shipment")
    public ShipmentDto createShipment(CreateShipmentRequest request, UserPrincipal principal) {
        Truck truck = truckRepository.findLockedById(request.truckId())
                .orElseThrow(() -> ApiException.notFound("Грузовик не найден"));
        User driver = userRepository.findLockedById(request.driverId())
                .orElseThrow(() -> ApiException.notFound("Водитель не найден"));
        Long originWarehouseId = isAdmin(principal)
                ? truck.getHomeWarehouse().getId()
                : resolveWarehouseId(principal, null);
        if (originWarehouseId.equals(request.destinationWarehouseId())) {
            throw ApiException.badRequest("Склады отправления и назначения должны различаться");
        }

        Warehouse origin = loadWarehouse(originWarehouseId);
        Warehouse destination = loadWarehouse(request.destinationWarehouseId());

        requireWarehouse(truck.getHomeWarehouse().getId(), originWarehouseId);
        if (truck.getStatus() != TruckStatus.IDLE) {
            throw ApiException.conflict("Грузовик сейчас недоступен");
        }
        if (driver.getRole() != Role.DRIVER || driver.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.conflict("Выбранный пользователь не является активным водителем");
        }
        requireWarehouse(driver.getWarehouseId(), originWarehouseId);
        if (shipmentRepository.existsByTruckIdAndStatusIn(truck.getId(), ACTIVE_SHIPMENT_STATUSES)) {
            throw ApiException.conflict("Грузовик уже назначен на активный рейс");
        }
        if (shipmentRepository.existsByDriverIdAndStatusIn(driver.getId(), ACTIVE_SHIPMENT_STATUSES)) {
            throw ApiException.conflict("Водитель уже назначен на активный рейс");
        }

        Shipment shipment = shipmentRepository.saveAndFlush(Shipment.builder()
                .truck(truck)
                .driver(driver)
                .originWarehouse(origin)
                .destinationWarehouse(destination)
                .status(ShipmentStatus.PLANNED)
                .plannedDepartureAt(request.plannedDepartureAt())
                .build());
        routeService.ensureRoute(shipment);
        return shipmentMapper.toDto(shipment);
    }

    @Transactional
    @Auditable(action = AuditAction.SHIPMENT_PARCEL_ASSIGNED, entityType = "Shipment")
    public ShipmentDto loadParcels(Long shipmentId, LoadParcelsRequest request,
                                   UserPrincipal principal) {
        Shipment shipment = loadLockedShipmentForActor(shipmentId, principal);
        if (shipment.getStatus() != ShipmentStatus.PLANNED
                && shipment.getStatus() != ShipmentStatus.LOADING) {
            throw new IllegalStateTransitionException(
                    "рейса", shipment.getStatus(), ShipmentStatus.LOADING);
        }

        Set<Long> requestedIds = new HashSet<>(request.parcelIds());
        if (requestedIds.size() != request.parcelIds().size()) {
            throw ApiException.badRequest("Список посылок содержит дубликаты");
        }

        List<Parcel> parcels = parcelRepository.findAllLockedByIdIn(requestedIds);
        if (parcels.size() != requestedIds.size()) {
            throw ApiException.notFound("Одна или несколько посылок не найдены");
        }

        Set<Long> alreadyLoaded = shipment.getParcelLinks().stream()
                .map(link -> link.getParcel().getId())
                .collect(java.util.stream.Collectors.toSet());
        BigDecimal addedWeight = BigDecimal.ZERO;
        for (Parcel parcel : parcels) {
            if (alreadyLoaded.contains(parcel.getId())) {
                throw ApiException.conflict(
                        "Посылка уже загружена в этот рейс: " + parcel.getTrackingNumber());
            }
            if (parcel.getStatus() != ParcelStatus.ACCEPTED_AT_ORIGIN) {
                throw new IllegalStateTransitionException(
                        "посылки", parcel.getStatus(), ParcelStatus.LOADED);
            }
            requireWarehouse(
                    parcel.getOriginWarehouse().getId(),
                    shipment.getOriginWarehouse().getId());
            requireWarehouse(
                    parcel.getDestinationWarehouse().getId(),
                    shipment.getDestinationWarehouse().getId());
            addedWeight = addedWeight.add(parcel.getWeightKg());
        }

        BigDecimal resultingWeight = shipmentMapper.loadedWeight(shipment).add(addedWeight);
        if (resultingWeight.compareTo(shipment.getTruck().getCapacityKg()) > 0) {
            throw ApiException.conflict(
                    "Грузоподъёмность превышена: %.2f кг из %.2f кг"
                            .formatted(resultingWeight, shipment.getTruck().getCapacityKg()));
        }

        if (shipment.getStatus() == ShipmentStatus.PLANNED) {
            changeStatus(shipment, ShipmentStatus.LOADING);
        }
        for (Parcel parcel : parcels) {
            ShipmentParcel link = ShipmentParcel.builder()
                    .id(new ShipmentParcelId(shipment.getId(), parcel.getId()))
                    .shipment(shipment)
                    .parcel(parcel)
                    .build();
            shipment.getParcelLinks().add(link);
            parcelService.changeStatus(
                    parcel,
                    ParcelStatus.LOADED,
                    "Посылка загружена в рейс #" + shipment.getId(),
                    shipment.getOriginWarehouse(),
                    principal.getId());
        }

        shipmentRepository.saveAndFlush(shipment);
        return shipmentMapper.toDto(shipment);
    }

    @Transactional
    @Auditable(action = AuditAction.SHIPMENT_PARCEL_REMOVED, entityType = "Shipment")
    public ShipmentDto removeParcel(Long shipmentId, Long parcelId, UserPrincipal principal) {
        Shipment shipment = loadLockedShipmentForActor(shipmentId, principal);
        if (shipment.getStatus() != ShipmentStatus.LOADING) {
            throw new IllegalStateTransitionException(
                    "рейса", shipment.getStatus(), ShipmentStatus.LOADING);
        }

        ShipmentParcel link = shipment.getParcelLinks().stream()
                .filter(candidate -> candidate.getParcel().getId().equals(parcelId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Посылка не загружена в этот рейс"));
        Parcel parcel = link.getParcel();
        shipment.getParcelLinks().remove(link);
        parcelService.changeStatus(
                parcel,
                ParcelStatus.ACCEPTED_AT_ORIGIN,
                "Посылка убрана из рейса #" + shipment.getId(),
                shipment.getOriginWarehouse(),
                principal.getId());
        shipmentRepository.saveAndFlush(shipment);
        return shipmentMapper.toDto(shipment);
    }

    @Transactional(readOnly = true)
    public PageResponse<ShipmentDto> findShipments(UserPrincipal principal, ShipmentStatus status,
                                                   Pageable pageable) {
        Specification<Shipment> specification = (root, query, cb) -> cb.conjunction();
        if (!isAdmin(principal)) {
            Long warehouseId = resolveWarehouseId(principal, null);
            specification = specification.and(
                    (root, query, cb) -> cb.equal(
                            root.get("originWarehouse").get("id"), warehouseId));
        }
        if (status != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return PageResponse.of(toDetailedShipmentPage(
                shipmentRepository.findAll(specification, pageable), pageable));
    }

    @Transactional(readOnly = true)
    public ShipmentDto findShipment(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = shipmentRepository.findDetailedById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Рейс не найден"));
        requireWarehouseForActor(shipment.getOriginWarehouse().getId(), principal);
        return shipmentMapper.toDto(shipment);
    }

    private Shipment loadLockedShipmentForActor(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = shipmentRepository.findLockedById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Рейс не найден"));
        requireWarehouseForActor(shipment.getOriginWarehouse().getId(), principal);
        shipment.getParcelLinks().size();
        return shipment;
    }

    private Parcel loadLockedParcel(Long parcelId) {
        return parcelRepository.findLockedById(parcelId)
                .orElseThrow(() -> ApiException.notFound("Посылка не найдена"));
    }

    private Warehouse loadWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Склад не найден: " + warehouseId));
    }

    private Long resolveWarehouseId(UserPrincipal principal, Long requestedWarehouseId) {
        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
        Long assignedWarehouseId = actor.getWarehouseId();

        if (principal.role() == Role.ADMIN && requestedWarehouseId != null) {
            return requestedWarehouseId;
        }
        if (assignedWarehouseId == null) {
            throw ApiException.badRequest("Пользователь не привязан к складу");
        }
        if (requestedWarehouseId != null && !assignedWarehouseId.equals(requestedWarehouseId)) {
            throw new AccessDeniedException("Нельзя работать с чужим складом");
        }
        return assignedWarehouseId;
    }

    private void requireWarehouse(Long actualWarehouseId, Long expectedWarehouseId) {
        if (actualWarehouseId == null || !actualWarehouseId.equals(expectedWarehouseId)) {
            throw new AccessDeniedException("Ресурс относится к другому складу");
        }
    }

    private void requireWarehouseForActor(Long resourceWarehouseId, UserPrincipal principal) {
        if (!isAdmin(principal)) {
            requireWarehouse(resourceWarehouseId, resolveWarehouseId(principal, null));
        }
    }

    private boolean isAdmin(UserPrincipal principal) {
        return principal.role() == Role.ADMIN;
    }

    private Page<ShipmentDto> toDetailedShipmentPage(Page<Shipment> idPage, Pageable pageable) {
        if (idPage.isEmpty()) {
            return idPage.map(shipmentMapper::toDto);
        }
        Map<Long, Shipment> detailedById = shipmentRepository
                .findDetailedByIdIn(idPage.map(Shipment::getId).getContent())
                .stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));
        List<ShipmentDto> content = idPage.getContent().stream()
                .map(shipment -> detailedById.get(shipment.getId()))
                .map(shipmentMapper::toDto)
                .toList();
        return new PageImpl<>(content, pageable, idPage.getTotalElements());
    }

    private void changeStatus(Shipment shipment, ShipmentStatus target) {
        if (!shipment.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException("рейса", shipment.getStatus(), target);
        }
        shipment.setStatus(target);
    }

}
