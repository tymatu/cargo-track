package com.cargotrack.shipment;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.IllegalStateTransitionException;
import com.cargotrack.common.PageResponse;
import com.cargotrack.live.TruckPositionChangedEvent;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelService;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.routing.RouteService;
import com.cargotrack.shipment.dto.ShipmentDto;
import com.cargotrack.simulation.ShipmentDepartedEvent;
import com.cargotrack.truck.TruckStatus;
import com.cargotrack.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriverService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentMapper shipmentMapper;
    private final ParcelService parcelService;
    private final RouteService routeService;
    private final ShipmentLifecycleService lifecycleService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<ShipmentDto> findMy(
            UserPrincipal principal, ShipmentStatus status, Pageable pageable) {
        var page = status == null
                ? shipmentRepository.findByDriverId(principal.getId(), pageable)
                : shipmentRepository.findByDriverIdAndStatus(
                        principal.getId(), status, pageable);
        return PageResponse.of(toDetailedShipmentPage(page, pageable));
    }

    @Transactional(readOnly = true)
    public ShipmentDto findShipment(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = shipmentRepository.findDetailedById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Shipment not found"));
        requireAssignedDriver(shipment, principal);
        return shipmentMapper.toDto(shipment);
    }

    @Transactional
    @Auditable(action = AuditAction.SHIPMENT_DEPARTED, entityType = "Shipment")
    public ShipmentDto depart(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = loadLockedForDriver(shipmentId, principal);
        changeStatus(shipment, ShipmentStatus.IN_TRANSIT);
        if (shipment.getParcelLinks().isEmpty()) {
            throw ApiException.conflict("Нельзя отправить пустой рейс");
        }

        for (ShipmentParcel link : shipment.getParcelLinks()) {
            Parcel parcel = link.getParcel();
            parcelService.changeStatus(
                    parcel,
                    ParcelStatus.IN_TRANSIT,
                    "Shipment #" + shipment.getId() + " departed from origin warehouse",
                    shipment.getOriginWarehouse(),
                    principal.getId());
        }
        Instant departedAt = Instant.now();
        shipment.setDepartedAt(departedAt);
        shipment.getTruck().setStatus(TruckStatus.IN_TRANSIT);
        shipment.getTruck().setCurrentLat(shipment.getOriginWarehouse().getLatitude());
        shipment.getTruck().setCurrentLng(shipment.getOriginWarehouse().getLongitude());
        shipment.getTruck().setLastPositionAt(departedAt);
        routeService.ensureRoute(shipment);
        shipmentRepository.saveAndFlush(shipment);
        eventPublisher.publishEvent(new TruckPositionChangedEvent(shipmentId));
        eventPublisher.publishEvent(new ShipmentDepartedEvent(shipmentId));
        return shipmentMapper.toDto(shipment);
    }

    @Transactional
    public ShipmentDto arrive(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = loadLockedForDriver(shipmentId, principal);
        return lifecycleService.completeArrival(shipment.getId(), principal.getId());
    }

    private Shipment loadLockedForDriver(Long shipmentId, UserPrincipal principal) {
        Shipment shipment = shipmentRepository.findLockedById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Shipment not found"));
        requireAssignedDriver(shipment, principal);
        shipment.getParcelLinks().size();
        return shipment;
    }

    private void requireAssignedDriver(Shipment shipment, UserPrincipal principal) {
        if (principal.role() != Role.ADMIN
                && !shipment.getDriver().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Shipment is assigned to another driver");
        }
    }

    private void changeStatus(Shipment shipment, ShipmentStatus target) {
        if (!shipment.getStatus().canTransitionTo(target)) {
            throw new IllegalStateTransitionException(
                    "shipment", shipment.getStatus(), target);
        }
        shipment.setStatus(target);
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
}
