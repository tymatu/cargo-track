package com.cargotrack.audit;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.parcel.Parcel;
import com.cargotrack.parcel.ParcelRepository;
import com.cargotrack.parcel.TrackingEvent;
import com.cargotrack.parcel.TrackingEventRepository;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuditFlowTest {

    private static final String PASSWORD = "secret-password-1";
    private static final String USER_EMAIL = "audit-user@test.io";
    private static final String ADMIN_EMAIL = "audit-admin@test.io";

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
    private ParcelRepository parcelRepository;
    @Autowired
    private TrackingEventRepository trackingEventRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User user;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        trackingEventRepository.deleteAll();
        parcelRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        user = createUser(USER_EMAIL, Role.USER);
        createUser(ADMIN_EMAIL, Role.ADMIN);
        userToken = login(USER_EMAIL);
        adminToken = login(ADMIN_EMAIL);

        await(() -> auditLogRepository
                .findFirstByActionAndUsernameOrderByCreatedAtDesc(
                        AuditAction.LOGIN_SUCCESS, USER_EMAIL)
                .isPresent());
        await(() -> auditLogRepository
                .findFirstByActionAndUsernameOrderByCreatedAtDesc(
                        AuditAction.LOGIN_SUCCESS, ADMIN_EMAIL)
                .isPresent());
        auditLogRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        trackingEventRepository.deleteAll();
        parcelRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void parcelCreation_recordsActorActionRequestAndJpaAuditFields() throws Exception {
        JsonNode parcelJson = createParcel();
        long parcelId = parcelJson.get("id").asLong();

        AuditLog audit = awaitValue(() -> auditLogRepository
                .findFirstByActionAndEntityIdOrderByCreatedAtDesc(
                        AuditAction.PARCEL_CREATED, parcelId)
                .orElse(null));

        assertThat(audit.getUserId()).isEqualTo(user.getId());
        assertThat(audit.getUsername()).isEqualTo(USER_EMAIL);
        assertThat(audit.getEntityType()).isEqualTo("Parcel");
        assertThat(audit.getIpAddress()).isEqualTo("203.0.113.42");
        assertThat(audit.getUserAgent()).isEqualTo("CargoTrack-Audit-Test/1.0");
        assertThat(audit.getHttpMethod()).isEqualTo("POST");
        assertThat(audit.getEndpoint()).isEqualTo("/api/v1/parcels");
        assertThat(audit.getNewValue().get("id").asLong()).isEqualTo(parcelId);

        Parcel parcel = parcelRepository.findById(parcelId).orElseThrow();
        TrackingEvent event = trackingEventRepository
                .findByParcelIdOrderByCreatedAtAsc(parcelId)
                .getFirst();
        assertThat(parcel.getCreatedBy()).isEqualTo(user.getId());
        assertThat(parcel.getUpdatedBy()).isEqualTo(user.getId());
        assertThat(event.getCreatedBy()).isEqualTo(user.getId());
    }

    @Test
    void adminAuditEndpoint_filtersResultsAndRejectsUser() throws Exception {
        long parcelId = createParcel().get("id").asLong();
        awaitValue(() -> auditLogRepository
                .findFirstByActionAndEntityIdOrderByCreatedAtDesc(
                        AuditAction.PARCEL_CREATED, parcelId)
                .orElse(null));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("action", "PARCEL_CREATED")
                        .param("entityType", "Parcel")
                        .param("entityId", Long.toString(parcelId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].userId").value(user.getId()))
                .andExpect(jsonPath("$.content[0].entityId").value(parcelId))
                .andExpect(jsonPath("$.content[0].ipAddress").value("203.0.113.42"));

        mockMvc.perform(get("/api/v1/admin/audit")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    private User createUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Audit")
                .lastName("Tester")
                .role(role)
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

    private JsonNode createParcel() throws Exception {
        String body = """
                {"originWarehouseId":1,"destinationWarehouseId":2,
                 "recipientName":"Audit Recipient","recipientPhone":"+420777888999",
                 "recipientEmail":"recipient@test.io","weightKg":10,
                 "lengthCm":30,"widthCm":20,"heightCm":10,"declaredValue":100}
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", "Bearer " + userToken)
                        .header("User-Agent", "CargoTrack-Audit-Test/1.0")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.42");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result);
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(
                result.getResponse().getContentAsString(StandardCharsets.UTF_8));
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

    private <T> T awaitValue(Supplier<T> supplier) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for asynchronous audit value");
    }
}
