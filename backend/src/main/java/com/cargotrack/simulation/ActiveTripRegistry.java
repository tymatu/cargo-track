package com.cargotrack.simulation;

import com.cargotrack.config.SimulationProperties;
import com.cargotrack.routing.Route;
import com.cargotrack.routing.RouteRepository;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
public class ActiveTripRegistry {

    private final ConcurrentMap<Long, ActiveTrip> trips = new ConcurrentHashMap<>();
    private final RouteRepository routeRepository;
    private final ShipmentRepository shipmentRepository;
    private final SimulationProperties properties;

    public Collection<ActiveTrip> activeTrips() {
        return List.copyOf(trips.values());
    }

    public void remove(Long shipmentId) {
        trips.remove(shipmentId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeparted(ShipmentDepartedEvent event) {
        start(event.shipmentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onArrived(ShipmentArrivedEvent event) {
        remove(event.shipmentId());
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void restoreTrips() {
        if (!properties.enabled()) {
            return;
        }
        shipmentRepository.findAllByStatus(ShipmentStatus.IN_TRANSIT)
                .forEach(this::start);
    }

    @Transactional(readOnly = true)
    public void start(Long shipmentId) {
        if (!properties.enabled()) {
            return;
        }
        routeRepository.findDetailedByShipmentId(shipmentId)
                .ifPresent(route -> start(route.getShipment(), route));
    }

    private void start(Shipment shipment) {
        start(shipment, shipment.getRoute());
    }

    private void start(Shipment shipment, Route route) {
        if (shipment.getStatus() != ShipmentStatus.IN_TRANSIT
                || route == null
                || route.getGeometry() == null
                || route.getGeometry().size() < 2) {
            return;
        }
        Instant now = Instant.now();
        Instant departedAt = shipment.getDepartedAt() == null ? now : shipment.getDepartedAt();
        ActiveTrip trip = new ActiveTrip(shipment.getId(), route.getGeometry(), departedAt);
        trip.advanceTo(now, properties.speedKmh(), properties.timeScale());
        trips.put(shipment.getId(), trip);
    }
}
