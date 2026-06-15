package com.cargotrack.simulation;

import com.cargotrack.config.SimulationProperties;
import com.cargotrack.shipment.ShipmentLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationEngine {

    private final ActiveTripRegistry registry;
    private final TruckPositionService positionService;
    private final ShipmentLifecycleService lifecycleService;
    private final SimulationProperties properties;

    @Scheduled(fixedDelayString = "${app.simulation.tick-delay:2s}")
    public void tick() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        for (ActiveTrip trip : registry.activeTrips()) {
            try {
                TripPosition position = trip.advanceTo(
                        now, properties.speedKmh(), properties.timeScale());
                if (!positionService.update(trip.shipmentId(), position, now)) {
                    registry.remove(trip.shipmentId());
                } else if (position.completed()) {
                    lifecycleService.completeArrival(trip.shipmentId(), null);
                }
            } catch (RuntimeException exception) {
                log.warn("Simulation tick failed for shipment {}: {}",
                        trip.shipmentId(), exception.getMessage());
            }
        }
    }
}
