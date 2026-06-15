package com.cargotrack.shipment;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.IllegalStateTransitionException;
import com.cargotrack.live.TruckPositionChangedEvent;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelService;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.shipment.dto.ShipmentDto;
import com.cargotrack.simulation.ShipmentArrivedEvent;
import com.cargotrack.truck.TruckStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ShipmentLifecycleService {

    private final ShipmentRepository shipmentRepository;
    private final ShipmentMapper shipmentMapper;
    private final ParcelService parcelService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Auditable(action = AuditAction.SHIPMENT_ARRIVED, entityType = "Shipment")
    public ShipmentDto completeArrival(Long shipmentId, Long actorId) {
        Shipment shipment = shipmentRepository.findLockedById(shipmentId)
                .orElseThrow(() -> ApiException.notFound("Shipment not found"));
        shipment.getParcelLinks().size();
        if (!shipment.getStatus().canTransitionTo(ShipmentStatus.COMPLETED)) {
            throw new IllegalStateTransitionException(
                    "shipment", shipment.getStatus(), ShipmentStatus.COMPLETED);
        }
        shipment.setStatus(ShipmentStatus.COMPLETED);

        for (ShipmentParcel link : shipment.getParcelLinks()) {
            Parcel parcel = link.getParcel();
            parcelService.changeStatus(
                    parcel,
                    ParcelStatus.ARRIVED_AT_DESTINATION,
                    "Parcel arrived at " + shipment.getDestinationWarehouse().getName(),
                    shipment.getDestinationWarehouse(),
                    actorId);
        }
        Instant arrivedAt = Instant.now();
        shipment.setArrivedAt(arrivedAt);
        shipment.getTruck().setStatus(TruckStatus.IDLE);
        shipment.getTruck().setCurrentLat(shipment.getDestinationWarehouse().getLatitude());
        shipment.getTruck().setCurrentLng(shipment.getDestinationWarehouse().getLongitude());
        shipment.getTruck().setLastPositionAt(arrivedAt);
        shipmentRepository.saveAndFlush(shipment);
        eventPublisher.publishEvent(new TruckPositionChangedEvent(shipmentId));
        eventPublisher.publishEvent(new ShipmentArrivedEvent(shipmentId));
        return shipmentMapper.toDto(shipment);
    }
}
