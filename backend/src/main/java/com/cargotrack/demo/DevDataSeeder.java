package com.cargotrack.demo;

import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.TrackingEvent;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.routing.Route;
import com.cargotrack.routing.RoutePoint;
import com.cargotrack.routing.RouteRepository;
import com.cargotrack.routing.RouteSource;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentParcel;
import com.cargotrack.shipment.ShipmentParcelId;
import com.cargotrack.shipment.ShipmentParcelRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
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
import net.datafaker.Faker;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Profile({"dev", "demo"})
@ConditionalOnProperty(name = "app.demo.seed-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "CargoTrack123!";
    public static final String DEMO_USER_EMAIL = "user@cargotrack.local";
    public static final String DEMO_ADMIN_EMAIL = "admin@cargotrack.local";
    private static final String MARKER_TRACKING_NUMBER = "CT-DEMO00001";

    private final WarehouseRepository warehouseRepository;
    private final TruckRepository truckRepository;
    private final UserRepository userRepository;
    private final ParcelRepository parcelRepository;
    private final TrackingEventRepository trackingEventRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentParcelRepository shipmentParcelRepository;
    private final RouteRepository routeRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Warehouse> warehouses = warehouseRepository.findAll().stream()
                .sorted(Comparator.comparing(Warehouse::getId))
                .toList();
        if (warehouses.size() < 6) {
            log.warn("Demo seed skipped: expected at least 6 warehouses, found {}", warehouses.size());
            return;
        }

        User demoUser = ensureUser(
                DEMO_USER_EMAIL, "Demo", "Customer", Role.USER, null);
        ensureUser(DEMO_ADMIN_EMAIL, "Demo", "Administrator", Role.ADMIN, null);
        Map<Long, User> drivers = ensureEmployees(warehouses, Role.DRIVER);
        ensureEmployees(warehouses, Role.DISPATCHER);
        Map<Long, Truck> trucks = ensureTrucks(warehouses);

        if (parcelRepository.existsByTrackingNumber(MARKER_TRACKING_NUMBER)) {
            log.info("CargoTrack demo data already present");
            return;
        }

        List<Parcel> inTransit = seedParcels(warehouses, demoUser);
        seedActiveShipments(warehouses, drivers, trucks, inTransit);
        log.info("CargoTrack demo seed ready: {} users, {} trucks, {} parcels, {} active shipments",
                userRepository.count(), truckRepository.count(), parcelRepository.count(),
                shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT));
    }

    private User ensureUser(
            String email, String firstName, String lastName, Role role, Long warehouseId) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode(DEMO_PASSWORD))
                        .firstName(firstName)
                        .lastName(lastName)
                        .phone("+420700000000")
                        .role(role)
                        .status(UserStatus.ACTIVE)
                        .warehouseId(warehouseId)
                        .build()));
    }

    private Map<Long, User> ensureEmployees(List<Warehouse> warehouses, Role role) {
        Map<Long, User> employees = new LinkedHashMap<>();
        for (Warehouse warehouse : warehouses) {
            List<User> existing = userRepository
                    .findByRoleAndWarehouseIdAndStatusOrderByLastNameAscFirstNameAsc(
                            role, warehouse.getId(), UserStatus.ACTIVE);
            User employee = existing.isEmpty()
                    ? ensureUser(
                            role.name().toLowerCase(Locale.ROOT) + "."
                                    + slug(warehouse.getCity()) + "@cargotrack.local",
                            warehouse.getCity(),
                            role == Role.DRIVER ? "Driver" : "Dispatcher",
                            role,
                            warehouse.getId())
                    : existing.getFirst();
            employees.put(warehouse.getId(), employee);
        }
        return employees;
    }

    private Map<Long, Truck> ensureTrucks(List<Warehouse> warehouses) {
        List<TruckSeed> seeds = List.of(
                new TruckSeed("CT-PRG-03", "MAN TGX", 11_000, 0),
                new TruckSeed("CT-BRN-02", "Iveco S-Way", 9_500, 1),
                new TruckSeed("CT-OST-02", "Renault T", 9_000, 2),
                new TruckSeed("CT-PLZ-01", "Volvo FM", 8_500, 3),
                new TruckSeed("CT-WIN-01", "Mercedes Actros", 12_000, 4),
                new TruckSeed("CT-DRS-01", "Scania R", 10_500, 5));
        Map<String, Truck> byPlate = new LinkedHashMap<>();
        truckRepository.findAll().forEach(truck -> byPlate.put(truck.getPlateNumber(), truck));
        for (TruckSeed seed : seeds) {
            Warehouse warehouse = warehouses.get(seed.warehouseIndex());
            byPlate.computeIfAbsent(seed.plate(), ignored -> truckRepository.save(Truck.builder()
                    .plateNumber(seed.plate())
                    .model(seed.model())
                    .capacityKg(BigDecimal.valueOf(seed.capacityKg()))
                    .status(TruckStatus.IDLE)
                    .homeWarehouse(warehouse)
                    .currentLat(warehouse.getLatitude())
                    .currentLng(warehouse.getLongitude())
                    .lastPositionAt(Instant.now())
                    .build()));
        }

        Map<Long, Truck> byWarehouse = new LinkedHashMap<>();
        byPlate.values().stream()
                .filter(truck -> truck.getStatus() == TruckStatus.IDLE)
                .forEach(truck -> byWarehouse.putIfAbsent(
                        truck.getHomeWarehouse().getId(), truck));
        return byWarehouse;
    }

    private List<Parcel> seedParcels(List<Warehouse> warehouses, User sender) {
        List<Parcel> inTransit = new ArrayList<>();
        Faker faker = new Faker(Locale.ENGLISH);
        for (int index = 0; index < 40; index++) {
            Warehouse origin = warehouses.get(index % warehouses.size());
            Warehouse destination = warehouses.get((index + 1 + index / 6) % warehouses.size());
            if (origin.getId().equals(destination.getId())) {
                destination = warehouses.get((index + 2) % warehouses.size());
            }
            ParcelStatus status = demoStatus(index);
            Parcel parcel = parcelRepository.save(Parcel.builder()
                    .trackingNumber("CT-DEMO%05d".formatted(index + 1))
                    .sender(sender)
                    .recipientName(faker.name().fullName())
                    .recipientPhone("+420777%06d".formatted(index + 1))
                    .recipientEmail(faker.internet().emailAddress())
                    .originWarehouse(origin)
                    .destinationWarehouse(destination)
                    .weightKg(BigDecimal.valueOf(2 + index % 18))
                    .lengthCm(BigDecimal.valueOf(30 + index % 20))
                    .widthCm(BigDecimal.valueOf(20 + index % 15))
                    .heightCm(BigDecimal.valueOf(10 + index % 10))
                    .declaredValue(BigDecimal.valueOf(100 + index * 25L))
                    .price(BigDecimal.valueOf(18 + index * 2L))
                    .status(status)
                    .build());
            seedTimeline(parcel, status);
            if (status == ParcelStatus.IN_TRANSIT) {
                inTransit.add(parcel);
            }
        }
        return inTransit;
    }

    private ParcelStatus demoStatus(int index) {
        if (index < 6 || index == 39) return ParcelStatus.CREATED;
        if (index < 12) return ParcelStatus.ACCEPTED_AT_ORIGIN;
        if (index < 18) return ParcelStatus.DELIVERED;
        if (index < 24) return ParcelStatus.CANCELLED;
        if (index < 30) return ParcelStatus.ARRIVED_AT_DESTINATION;
        return ParcelStatus.IN_TRANSIT;
    }

    private void seedTimeline(Parcel parcel, ParcelStatus finalStatus) {
        saveEvent(parcel, ParcelStatus.CREATED, parcel.getOriginWarehouse());
        if (finalStatus == ParcelStatus.CREATED) return;
        if (finalStatus == ParcelStatus.CANCELLED) {
            saveEvent(parcel, ParcelStatus.CANCELLED, parcel.getOriginWarehouse());
            return;
        }
        saveEvent(parcel, ParcelStatus.ACCEPTED_AT_ORIGIN, parcel.getOriginWarehouse());
        if (finalStatus == ParcelStatus.ACCEPTED_AT_ORIGIN) return;
        saveEvent(parcel, ParcelStatus.LOADED, parcel.getOriginWarehouse());
        saveEvent(parcel, ParcelStatus.IN_TRANSIT, parcel.getOriginWarehouse());
        if (finalStatus == ParcelStatus.IN_TRANSIT) return;
        saveEvent(parcel, ParcelStatus.ARRIVED_AT_DESTINATION, parcel.getDestinationWarehouse());
        if (finalStatus == ParcelStatus.DELIVERED) {
            saveEvent(parcel, ParcelStatus.DELIVERED, parcel.getDestinationWarehouse());
        }
    }

    private void saveEvent(Parcel parcel, ParcelStatus status, Warehouse warehouse) {
        trackingEventRepository.save(TrackingEvent.builder()
                .parcel(parcel)
                .status(status)
                .description("Demo: " + status.name().toLowerCase(Locale.ROOT).replace('_', ' '))
                .warehouse(warehouse)
                .build());
    }

    private void seedActiveShipments(
            List<Warehouse> warehouses,
            Map<Long, User> drivers,
            Map<Long, Truck> trucks,
            List<Parcel> inTransit) {
        for (int index = 0; index < 3; index++) {
            Warehouse origin = warehouses.get(index);
            Warehouse destination = warehouses.get(index + 3);
            Truck truck = trucks.get(origin.getId());
            User driver = drivers.get(origin.getId());
            if (truck == null || driver == null) {
                log.warn("Skipping demo shipment from {}: truck or driver is missing", origin.getName());
                continue;
            }

            Instant departedAt = Instant.now().minusSeconds(index * 15L);
            Shipment shipment = shipmentRepository.save(Shipment.builder()
                    .truck(truck)
                    .driver(driver)
                    .originWarehouse(origin)
                    .destinationWarehouse(destination)
                    .status(ShipmentStatus.IN_TRANSIT)
                    .plannedDepartureAt(departedAt.minusSeconds(900))
                    .departedAt(departedAt)
                    .build());

            List<Parcel> manifest = inTransit.subList(index * 3, index * 3 + 3);
            for (Parcel parcel : manifest) {
                shipmentParcelRepository.save(ShipmentParcel.builder()
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
                    .distanceKm(BigDecimal.valueOf(180 + index * 70L))
                    .durationMin(150 + index * 45)
                    .originLatitude(origin.getLatitude())
                    .originLongitude(origin.getLongitude())
                    .destinationLatitude(destination.getLatitude())
                    .destinationLongitude(destination.getLongitude())
                    .geometry(List.of(
                            new RoutePoint(origin.getLatitude().doubleValue(),
                                    origin.getLongitude().doubleValue()),
                            new RoutePoint(midLat, midLng),
                            new RoutePoint(destination.getLatitude().doubleValue(),
                                    destination.getLongitude().doubleValue())))
                    .source(RouteSource.FALLBACK)
                    .build());

            truck.setStatus(TruckStatus.IN_TRANSIT);
            truck.setCurrentLat(BigDecimal.valueOf(midLat));
            truck.setCurrentLng(BigDecimal.valueOf(midLng));
            truck.setLastPositionAt(Instant.now());
        }
    }

    private String slug(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private record TruckSeed(
            String plate, String model, long capacityKg, int warehouseIndex) {
    }
}
