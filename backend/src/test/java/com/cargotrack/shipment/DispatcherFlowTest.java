package com.cargotrack.shipment;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.audit.AuditLogRepository;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.parcel.ParcelRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DispatcherFlowTest {

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
    private String otherDispatcherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        origin = warehouseRepository.findById(1L).orElseThrow();
        destination = warehouseRepository.findById(2L).orElseThrow();

        userToken = createUserAndLogin("phase4-user@test.io", Role.USER, null);
        dispatcherToken = createUserAndLogin(
                "phase4-dispatcher@test.io", Role.DISPATCHER, origin.getId());
        otherDispatcherToken = createUserAndLogin(
                "phase4-other-dispatcher@test.io", Role.DISPATCHER, destination.getId());
        adminToken = createUserAndLogin("phase4-admin@test.io", Role.ADMIN, null);
        driver = createUser("phase4-driver@test.io", Role.DRIVER, origin.getId());
        truck = truckRepository.save(Truck.builder()
                .plateNumber("TEST-PHASE4")
                .model("Test Truck")
                .capacityKg(new BigDecimal("100.00"))
                .status(TruckStatus.IDLE)
                .homeWarehouse(origin)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void fullDispatcherFlow_acceptCreateAndLoad_reachesLoaded() throws Exception {
        JsonNode parcel = createParcel("60");
        long parcelId = parcel.get("id").asLong();

        mockMvc.perform(get("/api/v1/dispatcher/parcels")
                        .header("Authorization", bearer(dispatcherToken))
                        .param("status", "CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_AT_ORIGIN"));

        JsonNode shipment = createShipment();
        long shipmentId = shipment.get("id").asLong();
        assertThat(shipment.get("status").asText()).isEqualTo("PLANNED");

        mockMvc.perform(post("/api/v1/dispatcher/shipments/{id}/parcels", shipmentId)
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelIds\":[" + parcelId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOADING"))
                .andExpect(jsonPath("$.loadedWeightKg").value(60.0))
                .andExpect(jsonPath("$.parcels[0].status").value("LOADED"));

        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus().name())
                .isEqualTo("LOADED");
        assertThat(trackingEventRepository.findByParcelIdOrderByCreatedAtAsc(parcelId))
                .extracting(event -> event.getStatus().name())
                .containsExactly("CREATED", "ACCEPTED_AT_ORIGIN", "LOADED");
    }

    @Test
    void parcelsCanBeFilteredByDestinationWarehouse() throws Exception {
        long matchingParcelId = createParcel("10").get("id").asLong();
        long otherDestinationParcelId = createParcelToDestination("15", 3).get("id").asLong();
        accept(matchingParcelId);
        accept(otherDestinationParcelId);

        mockMvc.perform(get("/api/v1/dispatcher/parcels")
                        .header("Authorization", bearer(dispatcherToken))
                        .param("status", "ACCEPTED_AT_ORIGIN")
                        .param("destinationWarehouseId", Long.toString(destination.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(matchingParcelId));
    }

    @Test
    void loadingAboveCapacity_returns409AndRollsBackAllParcels() throws Exception {
        long firstId = createParcel("60").get("id").asLong();
        long secondId = createParcel("50").get("id").asLong();
        accept(firstId);
        accept(secondId);
        long shipmentId = createShipment().get("id").asLong();

        mockMvc.perform(post("/api/v1/dispatcher/shipments/{id}/parcels", shipmentId)
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelIds\":[" + firstId + "," + secondId + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("Грузоподъёмность превышена")));

        assertThat(parcelRepository.findById(firstId).orElseThrow().getStatus().name())
                .isEqualTo("ACCEPTED_AT_ORIGIN");
        assertThat(parcelRepository.findById(secondId).orElseThrow().getStatus().name())
                .isEqualTo("ACCEPTED_AT_ORIGIN");
        assertThat(shipmentRepository.findDetailedById(shipmentId).orElseThrow().getParcelLinks())
                .isEmpty();
        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.PLANNED);
    }

    @Test
    void removeParcelBeforeDeparture_returnsItToAccepted() throws Exception {
        long parcelId = createParcel("30").get("id").asLong();
        accept(parcelId);
        long shipmentId = createShipment().get("id").asLong();
        load(shipmentId, parcelId);

        mockMvc.perform(delete("/api/v1/dispatcher/shipments/{id}/parcels/{parcelId}",
                        shipmentId, parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcels.length()").value(0))
                .andExpect(jsonPath("$.loadedWeightKg").value(0));

        assertThat(parcelRepository.findById(parcelId).orElseThrow().getStatus().name())
                .isEqualTo("ACCEPTED_AT_ORIGIN");
    }

    @Test
    void dispatcherCannotAcceptParcelFromAnotherWarehouse() throws Exception {
        long parcelId = createParcel("10").get("id").asLong();

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(otherDispatcherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void repeatedAcceptAndEarlyDeliver_return409() throws Exception {
        long parcelId = createParcel("10").get("id").asLong();
        accept(parcelId);

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/deliver", parcelId)
                        .header("Authorization", bearer(otherDispatcherToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCreatesEmployeeBoundToWarehouse() throws Exception {
        String body = """
                {"email":"new-driver@test.io","password":"secret-password-2",
                 "firstName":"New","lastName":"Driver","phone":"+420700999999",
                 "role":"DRIVER","warehouseId":1}
                """;

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new-driver@test.io"))
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.warehouseId").value(1));
    }

    @Test
    void adminWithoutWarehouseCanUseDispatcherOperations() throws Exception {
        long parcelId = createParcel("20").get("id").asLong();

        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED_AT_ORIGIN"));

        JsonNode shipment = parse(mockMvc.perform(post("/api/v1/dispatcher/shipments")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"truckId":%d,"driverId":%d,"destinationWarehouseId":%d}
                                """.formatted(truck.getId(), driver.getId(), destination.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originWarehouse.id").value(origin.getId()))
                .andReturn());

        mockMvc.perform(post("/api/v1/dispatcher/shipments/{id}/parcels",
                        shipment.get("id").asLong())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parcelIds\":[" + parcelId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcels[0].status").value("LOADED"));
    }

    @Test
    void activeTruckAndDriverCannotBeAssignedTwice() throws Exception {
        createShipment();

        mockMvc.perform(post("/api/v1/dispatcher/shipments")
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"truckId":%d,"driverId":%d,"destinationWarehouseId":%d}
                                """.formatted(truck.getId(), driver.getId(), destination.getId())))
                .andExpect(status().isConflict());
    }

    private JsonNode createParcel(String weightKg) throws Exception {
        return createParcelToDestination(weightKg, 2);
    }

    private JsonNode createParcelToDestination(String weightKg, long destinationWarehouseId) throws Exception {
        String body = """
                {"originWarehouseId":1,"destinationWarehouseId":%d,
                 "recipientName":"Phase Four","recipientPhone":"+420777888999",
                 "recipientEmail":"phase4-recipient@test.io","weightKg":%s,
                 "lengthCm":30,"widthCm":20,"heightCm":10,"declaredValue":100}
                """.formatted(destinationWarehouseId, weightKg);
        return parse(mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void accept(long parcelId) throws Exception {
        mockMvc.perform(post("/api/v1/dispatcher/parcels/{id}/accept", parcelId)
                        .header("Authorization", bearer(dispatcherToken)))
                .andExpect(status().isOk());
    }

    private JsonNode createShipment() throws Exception {
        String body = """
                {"truckId":%d,"driverId":%d,"destinationWarehouseId":%d}
                """.formatted(truck.getId(), driver.getId(), destination.getId());
        return parse(mockMvc.perform(post("/api/v1/dispatcher/shipments")
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
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
                .lastName("Four")
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
