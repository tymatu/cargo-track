package com.cargotrack.demo;

import com.cargotrack.live.TruckPositionChangedEvent;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.ParcelService;
import com.cargotrack.parcel.TrackingEvent;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.parcel.dto.CreateParcelRequest;
import com.cargotrack.routing.Route;
import com.cargotrack.routing.RoutePoint;
import com.cargotrack.routing.RouteRepository;
import com.cargotrack.routing.RouteSource;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentParcel;
import com.cargotrack.shipment.ShipmentParcelId;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
import com.cargotrack.simulation.ShipmentDepartedEvent;
import com.cargotrack.truck.Truck;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.truck.TruckStatus;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import com.cargotrack.warehouse.Warehouse;
import com.cargotrack.warehouse.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(name = "app.demo.life-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DemoLifeGenerator {

    private static final long MAX_DEMO_PARCELS = 100;

    private final ParcelService parcelService;
    private final ParcelRepository parcelRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final UserRepository userRepository;
    private final WarehouseRepository warehouseRepository;
    private final ShipmentRepository shipmentRepository;
    private final RouteRepository routeRepository;
    private final TruckRepository truckRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicInteger sequence = new AtomicInteger(1);

    private static final EnumSet<ShipmentStatus> ACTIVE_SHIPMENT_STATUSES =
            EnumSet.of(ShipmentStatus.PLANNED, ShipmentStatus.LOADING, ShipmentStatus.IN_TRANSIT);

    @Scheduled(
            initialDelayString = "${app.demo.life-initial-delay:30s}",
            fixedDelayString = "${app.demo.life-delay:45s}")
    public void createParcel() {
        if (parcelRepository.count() >= MAX_DEMO_PARCELS) {
            return;
        }
        var sender = userRepository.findByEmail(DevDataSeeder.DEMO_USER_EMAIL);
        var warehouses = warehouseRepository.findAll();
        if (sender.isEmpty() || warehouses.size() < 2) {
            return;
        }
        int value = sequence.getAndIncrement();
        var origin = warehouses.get(value % warehouses.size());
        var destination = warehouses.get((value + 1) % warehouses.size());
        parcelService.create(new CreateParcelRequest(
                origin.getId(),
                destination.getId(),
                "Live Demo " + value,
                "+420799%06d".formatted(value % 1_000_000),
                "live%04d@example.test".formatted(value),
                BigDecimal.valueOf(2 + value % 20),
                BigDecimal.valueOf(40),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(250 + value * 10L)),
                sender.get().getId());
        log.info("Demo life created a parcel from {} to {}", origin.getCity(), destination.getCity());
    }

    @Transactional
    @Scheduled(
            initialDelayString = "${app.demo.fleet-initial-delay:20s}",
            fixedDelayString = "${app.demo.fleet-delay:2m}")
    public void keepFleetMoving() {
        if (shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT) >= 3) {
            return;
        }
        var sender = userRepository.findByEmail(DevDataSeeder.DEMO_USER_EMAIL);
        List<Warehouse> warehouses = warehouseRepository.findAll().stream()
                .sorted(Comparator.comparing(Warehouse::getId))
                .toList();
        if (sender.isEmpty() || warehouses.size() < 2) {
            return;
        }

        for (Truck truck : truckRepository.findByStatusOrderByPlateNumber(TruckStatus.IDLE)) {
            if (shipmentRepository.existsByTruckIdAndStatusIn(truck.getId(), ACTIVE_SHIPMENT_STATUSES)) {
                continue;
            }
            Warehouse origin = truck.getHomeWarehouse();
            Warehouse destination = nextWarehouse(warehouses, origin);
            User driver = availableDriver(origin.getId());
            if (driver == null) {
                continue;
            }

            Shipment shipment = createInTransitShipment(sender.get(), truck, driver, origin, destination);
            eventPublisher.publishEvent(new TruckPositionChangedEvent(shipment.getId()));
            eventPublisher.publishEvent(new ShipmentDepartedEvent(shipment.getId()));
            log.info("Demo life started shipment #{} from {} to {}",
                    shipment.getId(), origin.getCity(), destination.getCity());
            return;
        }
    }

    private User availableDriver(Long warehouseId) {
        return userRepository
                .findByRoleAndWarehouseIdAndStatusOrderByLastNameAscFirstNameAsc(
                        Role.DRIVER, warehouseId, UserStatus.ACTIVE)
                .stream()
                .filter(driver -> !shipmentRepository.existsByDriverIdAndStatusIn(
                        driver.getId(), ACTIVE_SHIPMENT_STATUSES))
                .findFirst()
                .orElse(null);
    }

    private Shipment createInTransitShipment(
            User sender,
            Truck truck,
            User driver,
            Warehouse origin,
            Warehouse destination) {
        Instant departedAt = Instant.now().minusSeconds(5);
        Shipment shipment = shipmentRepository.save(Shipment.builder()
                .truck(truck)
                .driver(driver)
                .originWarehouse(origin)
                .destinationWarehouse(destination)
                .status(ShipmentStatus.IN_TRANSIT)
                .plannedDepartureAt(departedAt.minusSeconds(600))
                .departedAt(departedAt)
                .build());

        for (int i = 0; i < 3; i++) {
            Parcel parcel = parcelRepository.save(Parcel.builder()
                    .trackingNumber("CT-LIVE%d-%d".formatted(
                            Instant.now().toEpochMilli(), i + 1))
                    .sender(sender)
                    .recipientName("Live Fleet Recipient " + (i + 1))
                    .recipientPhone("+420788%06d".formatted(sequence.getAndIncrement() % 1_000_000))
                    .recipientEmail("fleet%04d@example.test".formatted(sequence.get()))
                    .originWarehouse(origin)
                    .destinationWarehouse(destination)
                    .weightKg(BigDecimal.valueOf(4 + i))
                    .lengthCm(BigDecimal.valueOf(45))
                    .widthCm(BigDecimal.valueOf(30))
                    .heightCm(BigDecimal.valueOf(25))
                    .declaredValue(BigDecimal.valueOf(500 + i * 100L))
                    .price(BigDecimal.valueOf(24 + i * 3L))
                    .status(ParcelStatus.IN_TRANSIT)
                    .build());
            saveTimeline(parcel);
            shipment.getParcelLinks().add(ShipmentParcel.builder()
                    .id(new ShipmentParcelId(shipment.getId(), parcel.getId()))
                    .shipment(shipment)
                    .parcel(parcel)
                    .loadedAt(departedAt)
                    .build());
        }

        double midLat = (origin.getLatitude().doubleValue()
                + destination.getLatitude().doubleValue()) / 2;
        double midLng = (origin.getLongitude().doubleValue()
                + destination.getLongitude().doubleValue()) / 2;
        routeRepository.save(Route.builder()
                .shipment(shipment)
                .distanceKm(BigDecimal.valueOf(180))
                .durationMin(180)
                .geometry(List.of(
                        new RoutePoint(origin.getLatitude().doubleValue(),
                                origin.getLongitude().doubleValue()),
                        new RoutePoint(midLat, midLng),
                        new RoutePoint(destination.getLatitude().doubleValue(),
                                destination.getLongitude().doubleValue())))
                .source(RouteSource.FALLBACK)
                .build());

        truck.setStatus(TruckStatus.IN_TRANSIT);
        truck.setCurrentLat(origin.getLatitude());
        truck.setCurrentLng(origin.getLongitude());
        truck.setLastPositionAt(departedAt);
        return shipmentRepository.saveAndFlush(shipment);
    }

    private Warehouse nextWarehouse(List<Warehouse> warehouses, Warehouse origin) {
        int index = -1;
        for (int i = 0; i < warehouses.size(); i++) {
            if (warehouses.get(i).getId().equals(origin.getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return warehouses.getFirst();
        }
        return warehouses.get((index + 3) % warehouses.size());
    }

    private void saveTimeline(Parcel parcel) {
        saveEvent(parcel, ParcelStatus.CREATED, parcel.getOriginWarehouse());
        saveEvent(parcel, ParcelStatus.ACCEPTED_AT_ORIGIN, parcel.getOriginWarehouse());
        saveEvent(parcel, ParcelStatus.LOADED, parcel.getOriginWarehouse());
        saveEvent(parcel, ParcelStatus.IN_TRANSIT, parcel.getOriginWarehouse());
    }

    private void saveEvent(Parcel parcel, ParcelStatus status, Warehouse warehouse) {
        trackingEventRepository.save(TrackingEvent.builder()
                .parcel(parcel)
                .status(status)
                .description("Demo life: " + status.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .warehouse(warehouse)
                .build());
    }
}
