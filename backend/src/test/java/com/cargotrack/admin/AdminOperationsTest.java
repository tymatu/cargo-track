package com.cargotrack.admin;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.AuditLogRepository;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.shipment.Shipment;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.shipment.ShipmentStatus;
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
import jakarta.servlet.http.Cookie;
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
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminOperationsTest {

    private static final String PASSWORD = "secret-password-1";
    private static final String REFRESH_COOKIE = "ct_refresh_token";

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

    private User admin;
    private User user;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        admin = createUser("phase8-admin@test.io", Role.ADMIN);
        user = createUser("phase8-user@test.io", Role.USER);
        adminToken = login(admin.getEmail()).get("accessToken").asText();
        userToken = login(user.getEmail()).get("accessToken").asText();
        await(() -> auditLogRepository.count() >= 2);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void adminCrudDashboardAndAudit_workEndToEnd() throws Exception {
        long parcelId = createParcel().get("id").asLong();

        MvcResult warehouseResult = mockMvc.perform(post("/api/v1/admin/warehouses")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Phase8 Hub","city":"Olomouc","address":"Test 8",
                                 "latitude":49.593778,"longitude":17.250879}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Phase8 Hub"))
                .andReturn();
        long warehouseId = parse(warehouseResult).get("id").asLong();

        MvcResult truckResult = mockMvc.perform(post("/api/v1/admin/trucks")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"8AB 1234","model":"Phase 8",
                                 "capacityKg":2500,"status":"IDLE","homeWarehouseId":%d}
                                """.formatted(warehouseId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value("8AB 1234"))
                .andReturn();
        long truckId = parse(truckResult).get("id").asLong();

        mockMvc.perform(put("/api/v1/admin/trucks/{id}", truckId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plateNumber":"8AB 1234","model":"Phase 8 updated",
                                 "capacityKg":2600,"status":"MAINTENANCE","homeWarehouseId":%d}
                                """.formatted(warehouseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));

        mockMvc.perform(get("/api/v1/admin/stats/dashboard")
                        .header("Authorization", bearer(adminToken))
                        .param("from", Instant.now().minus(Duration.ofDays(1)).toString())
                        .param("to", Instant.now().plus(Duration.ofDays(1)).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersTotal").value(2))
                .andExpect(jsonPath("$.parcelsTotal").value(1))
                .andExpect(jsonPath("$.parcelsByStatus.CREATED").value(1))
                .andExpect(jsonPath("$.revenue", greaterThan(0.0)))
                .andExpect(jsonPath("$.trucksMaintenance").value(1));

        mockMvc.perform(get("/api/v1/admin/stats/dashboard")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/admin/trucks/{id}", truckId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/warehouses/{id}", warehouseId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());

        awaitAction(AuditAction.PARCEL_CREATED, parcelId);
        awaitAction(AuditAction.TRUCK_CREATED, truckId);
        awaitAction(AuditAction.TRUCK_UPDATED, truckId);
        awaitAction(AuditAction.TRUCK_DELETED, truckId);
        awaitAction(AuditAction.WAREHOUSE_DELETED, warehouseId);

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header("Authorization", bearer(adminToken))
                        .param("action", "TRUCK_UPDATED")
                        .param("entityType", "Truck")
                        .param("entityId", Long.toString(truckId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value(admin.getEmail()));
    }

    @Test
    void blockRevokesRefreshAndRoleChangeRequiresAdmin() throws Exception {
        MvcResult login = loginResult(user.getEmail());
        String refreshToken = refreshTokenFrom(login);

        mockMvc.perform(post("/api/v1/admin/users/{id}/block", user.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        awaitAction(AuditAction.USER_BLOCKED, user.getId());

        assertThat(refreshTokenRepository.findByToken(refreshToken).orElseThrow().isRevoked())
                .isTrue();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(userToken)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/users/{id}/unblock", user.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        awaitAction(AuditAction.USER_UNBLOCKED, user.getId());

        mockMvc.perform(patch("/api/v1/admin/users/{id}/role", user.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"DRIVER","warehouseId":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DRIVER"))
                .andExpect(jsonPath("$.warehouseId").value(1));
        awaitAction(AuditAction.USER_ROLE_CHANGED, user.getId());

        mockMvc.perform(post("/api/v1/admin/users/{id}/block", admin.getId())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void warehouseCoordinatesUsedByActiveShipmentCannotBeChanged() throws Exception {
        Warehouse origin = warehouseRepository.findById(1L).orElseThrow();
        Warehouse destination = warehouseRepository.findById(2L).orElseThrow();
        User driver = createUser("active-driver@test.io", Role.DRIVER);
        driver.setWarehouseId(origin.getId());
        driver = userRepository.save(driver);
        Truck truck = truckRepository.save(Truck.builder()
                .plateNumber("ACT-100")
                .model("Active route")
                .capacityKg(new BigDecimal("1500.00"))
                .status(TruckStatus.IDLE)
                .homeWarehouse(origin)
                .build());
        shipmentRepository.save(Shipment.builder()
                .truck(truck)
                .driver(driver)
                .originWarehouse(origin)
                .destinationWarehouse(destination)
                .status(ShipmentStatus.PLANNED)
                .build());

        mockMvc.perform(put("/api/v1/admin/warehouses/{id}", origin.getId())
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","city":"%s","address":"%s",
                                 "latitude":50.000000,"longitude":14.000000}
                                """.formatted(origin.getName(), origin.getCity(), origin.getAddress())))
                .andExpect(status().isConflict());
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Phase")
                .lastName("Eight")
                .role(role)
                .build());
    }

    private JsonNode login(String email) throws Exception {
        return parse(loginResult(email));
    }

    private MvcResult loginResult(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode createParcel() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originWarehouseId":1,"destinationWarehouseId":2,
                                 "recipientName":"Phase Eight","recipientPhone":"+420777111222",
                                 "weightKg":12,"lengthCm":30,"widthCm":20,"heightCm":10}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result);
    }

    private void awaitAction(AuditAction action, long entityId) {
        await(() -> auditLogRepository
                .findFirstByActionAndEntityIdOrderByCreatedAtDesc(action, entityId)
                .isPresent());
    }

    private void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for audit", exception);
            }
        }
        throw new AssertionError("Timed out waiting for asynchronous audit");
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String refreshTokenFrom(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
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
        warehouseRepository.findAll().stream()
                .filter(warehouse -> warehouse.getId() > 6)
                .forEach(warehouseRepository::delete);
    }
}
