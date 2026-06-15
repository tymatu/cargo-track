package com.cargotrack.security;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.audit.AuditLogRepository;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.shipment.ShipmentRepository;
import com.cargotrack.truck.TruckRepository;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessMatrixTest {

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
    private ShipmentRepository shipmentRepository;
    @Autowired
    private TrackingEventRepository trackingEventRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private TruckRepository truckRepository;

    private String userToken;
    private String driverToken;
    private String dispatcherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanDatabase();
        userToken = createAndLogin("matrix-user@test.io", Role.USER, null);
        driverToken = createAndLogin("matrix-driver@test.io", Role.DRIVER, 1L);
        dispatcherToken = createAndLogin("matrix-dispatcher@test.io", Role.DISPATCHER, 1L);
        adminToken = createAndLogin("matrix-admin@test.io", Role.ADMIN, null);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void roleMatrixProtectsAllApiAreas() throws Exception {
        mockMvc.perform(get("/api/v1/warehouses"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tracking/CT-NOT-FOUND"))
                .andExpect(status().isNotFound());

        expect(userToken, "/api/v1/parcels/my", 200);
        expect(userToken, "/api/v1/driver/shipments/my", 403);
        expect(userToken, "/api/v1/dispatcher/shipments", 403);
        expect(userToken, "/api/v1/admin/stats/dashboard", 403);

        expect(driverToken, "/api/v1/parcels/my", 403);
        expect(driverToken, "/api/v1/driver/shipments/my", 200);
        expect(driverToken, "/api/v1/dispatcher/shipments", 403);
        expect(driverToken, "/api/v1/admin/stats/dashboard", 403);

        expect(dispatcherToken, "/api/v1/parcels/my", 403);
        expect(dispatcherToken, "/api/v1/driver/shipments/my", 403);
        expect(dispatcherToken, "/api/v1/dispatcher/shipments", 200);
        expect(dispatcherToken, "/api/v1/admin/stats/dashboard", 403);

        expect(adminToken, "/api/v1/parcels/my", 200);
        expect(adminToken, "/api/v1/driver/shipments/my", 200);
        expect(adminToken, "/api/v1/dispatcher/shipments", 200);
        expect(adminToken, "/api/v1/admin/stats/dashboard", 200);

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CargoTrack API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }

    private void expect(String token, String path, int expectedStatus) throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus));
    }

    private String createAndLogin(String email, Role role, Long warehouseId) throws Exception {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Access")
                .lastName(role.name())
                .role(role)
                .warehouseId(warehouseId)
                .build());
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("accessToken").asText();
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
