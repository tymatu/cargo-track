package com.cargotrack.shipment;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.AuditLogRepository;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.ParcelStatus;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.truck.Truck;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.truck.TruckStatus;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.warehouse.Warehouse;
import com.cargotrack.warehouse.WarehouseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DriverFlowTest {

    private static final String PASSWORD = "secret-password-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private ShipmentRepository shipmentRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private TrackingEventRepository trackingEventRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private Warehouse origin;
    private Warehouse destination;
    private User driver;
    private Truck truck;
    private String userToken;
    private String dispatcherToken;
    private String destinationDispatcherToken;
    private String driverToken;
    private String otherDriverToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        origin = warehouseRepository.findById(1L).orElseThrow();
        destination = warehouseRepository.findById(2L).orElseThrow();

        userToken = createUserAndLogin("phase5-user@test.io", Role.USER, null);
        dispatcherToken = createUserAndLogin(
                "phase5-dispatcher@test.io", Role.DISPATCHER, origin.getId());
        destinationDispatcherToken = createUserAndLogin(
                "phase5-destination-dispatcher@test.io", Role.DISPATCHER, destination.getId());
        driver = createUser("phase5-driver@test.io", Role.DRIVER, origin.getId());
        driverToken = login(driver.getEmail());
        otherDriverToken = createUserAndLogin(
                "phase5-other-driver@test.io", Role.DRIVER, origin.getId());
        truck = truckRepository.save(Truck.builder()
                .plateNumber("TEST-PHASE5")
                .model("Driver Test Truck")
                .capacityKg(new BigDecimal("100.00"))
                .status(TruckStatus.IDLE)
                .homeWarehouse(origin)
                .build());
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void mainScenario_createDepartArriveAndDeliver_reachesDelivered() throws Exception {
        long parcelId = prepareLoadedShipmentParcel();
        long shipmentId = shipmentRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/api/v1/driver/shipments/my")
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(shipmentId));

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", shipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.truck.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.parcels[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.departedAt").isNotEmpty());

        Shipment departed = shipmentRepository.findById(shipmentId).orElseThrow();
        assertThat(departed.getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
        assertThat(departed.getDepartedAt()).isNotNull();
        assertThat(truckRepository.findById(truck.getId()).orElseThrow().getStatus())
                .isEqualTo(TruckStatus.IN_TRANSIT);
        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(trackingEventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId))
                .extracting(event -> event.getStatus().name())
                .containsExactly("CREATED", "ACCEPTED_AT_ORIGIN", "LOADED", "IN_TRANSIT");
        await(() -> auditLogRepository.findFirstByActionAndEntityIdOrderByCreatedAtDesc(
                AuditAction.SHIPMENT_DEPARTED, shipmentId).isPresent());

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/arrive", shipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.truck.status").value("IDLE"))
                .andExpect(jsonPath("$.parcels[0].status").value("ARRIVED_AT_DESTINATION"))
                .andExpect(jsonPath("$.arrivedAt").isNotEmpty());

        Shipment arrived = shipmentRepository.findById(shipmentId).orElseThrow();
        Truck arrivedTruck = truckRepository.findById(truck.getId()).orElseThrow();
        assertThat(arrived.getStatus()).isEqualTo(ShipmentStatus.COMPLETED);
        assertThat(arrived.getArrivedAt()).isNotNull();
        assertThat(arrivedTruck.getStatus()).isEqualTo(TruckStatus.IDLE);
        assertThat(arrivedTruck.getCurrentLat()).isEqualByComparingTo(destination.getLatitude());
        assertThat(arrivedTruck.getCurrentLng()).isEqualByComparingTo(destination.getLongitude());
        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.ARRIVED_AT_DESTINATION);
        assertThat(trackingEventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId))
                .extracting(event -> event.getStatus().name())
                .containsExactly(
                        "CREATED", "ACCEPTED_AT_ORIGIN", "LOADED",
                        "IN_TRANSIT", "ARRIVED_AT_DESTINATION");
        await(() -> auditLogRepository.findFirstByActionAndEntityIdOrderByCreatedAtDesc(
                AuditAction.SHIPMENT_ARRIVED, shipmentId).isPresent());

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/deliver", parcelId)
                        .header("Authorization", bearer(destinationDispatcherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus())
                .isEqualTo(ParcelStatus.DELIVERED);
        assertThat(trackingEventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId))
                .extracting(event -> event.getStatus().name())
                .containsExactly(
                        "CREATED", "ACCEPTED_AT_ORIGIN", "LOADED",
                        "IN_TRANSIT", "ARRIVED_AT_DESTINATION", "DELIVERED");
    }

    @Test
    void otherDriverCannotViewOrDepartShipment() throws Exception {
        prepareLoadedShipmentParcel();
        long shipmentId = shipmentRepository.findAll().getFirst().getId();

        mockMvc.perform(get("/api/v1/driver/shipments/{id}", shipmentId)
                        .header("Authorization", bearer(otherDriverToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", shipmentId)
                        .header("Authorization", bearer(otherDriverToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void departFromPlannedOrRepeatedDepart_returns409() throws Exception {
        long plannedShipmentId = createShipment();

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", plannedShipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isConflict());

        long parcelId = createParcel();
        accept(parcelId);
        load(plannedShipmentId, parcelId);
        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", plannedShipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", plannedShipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void emptyLoadingShipmentCannotDepart() throws Exception {
        long parcelId = prepareLoadedShipmentParcel();
        long shipmentId = shipmentRepository.findAll().getFirst().getId();

        mockMvc.perform(delete("/api/v1/dispatcher/shipments/{id}/parcels/{parcelId}",
                        shipmentId, parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/driver/shipments/{id}/depart", shipmentId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Нельзя отправить пустой рейс"));

        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.LOADING);
        assertThat(truckRepository.findById(truck.getId()).orElseThrow().getStatus())
                .isEqualTo(TruckStatus.IDLE);
    }

    private long prepareLoadedShipmentParcel() throws Exception {
        long parcelId = createParcel();
        accept(parcelId);
        long shipmentId = createShipment();
        load(shipmentId, parcelId);
        return parcelId;
    }

    private long createParcel() throws Exception {
        String body = """
                {"originWarehouseId":1,"destinationWarehouseId":2,
                 "recipientName":"Phase Five","recipientPhone":"+420777555999",
                 "recipientEmail":"phase5-recipient@test.io","weightKg":25,
                 "lengthCm":30,"widthCm":20,"heightCm":10,"declaredValue":100}
                """;
        return parse(mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private void accept(long parcelId) throws Exception {
        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isOk());
    }

    private long createShipment() throws Exception {
        String body = """
                {"truckId":%d,"driverId":%d,"destinationWarehouseId":%d}
                """.formatted(truck.getId(), driver.getId(), destination.getId());
        return parse(mockMvc.perform(post("/api/v1/dispatcher/shipments")
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asLong();
    }

    private void load(long shipmentId, long parcelId) throws Exception {
        mockMvc.perform(post("/api/v1/dispatcher/shipments/{id}/parcels", shipmentId)
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelIds\":[" + parcelId + "]}"))
                .andExpect(status().isOk());
    }

    private String createUserAndLogin(String email, Role role, Long warehouseId) throws Exception {
        createUser(email, role, warehouseId);
        return login(email);
    }

    private User createUser(String email, Role role, Long warehouseId) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Phase")
                .lastName("Five")
                .role(role)
                .warehouseId(warehouseId)
                .build());
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return parse(result).get("accessToken").asText();
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void await(Supplier<Boolean> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for asynchronous audit record");
    }

    private void cleanDatabase() {
        shipmentRepository.deleteAll();
        trackingEventRepository.deleteAll();
        parcelRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        truckRepository.deleteAll();
        userRepository.deleteAll();
    }
}
