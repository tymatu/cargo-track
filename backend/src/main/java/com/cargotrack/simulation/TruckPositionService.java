package com.cargotrack.simulation;

import com.cargotrack.live.TruckPositionChangedEvent;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TruckPositionService {

    private final ShipmentRepository shipmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean update(Long shipmentId, TripPosition position, Instant recordedAt) {
        Shipment shipment = shipmentRepository.findLockedById(shipmentId).orElse(null);
        if (shipment == null || shipment.getStatus() != ShipmentStatus.IN_TRANSIT) {
            return false;
        }
        shipment.getTruck().setCurrentLat(BigDecimal.valueOf(position.point().latitude()));
        shipment.getTruck().setCurrentLng(BigDecimal.valueOf(position.point().longitude()));
        shipment.getTruck().setLastPositionAt(recordedAt);
        eventPublisher.publishEvent(new TruckPositionChangedEvent(shipmentId));
        return true;
    }
}
