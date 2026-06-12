package com.cargotrack.parcel;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.auth.RefreshTokenRepository;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ParcelFlowTest {

    private static final String PASSWORD = "secret-password-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ParcelRepository parcelRepository;
    @Autowired
    private TrackingEventRepository eventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private String ownerToken;
    private String strangerToken;
    private String dispatcherToken;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        eventRepository.deleteAll();
        parcelRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        ownerToken = createUserAndLogin("owner@test.io", Role.USER);
        strangerToken = createUserAndLogin("stranger@test.io", Role.USER);
        dispatcherToken = createUserAndLogin("dispatcher@test.io", Role.DISPATCHER);
        adminToken = createUserAndLogin("admin@test.io", Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        // не оставляем за собой посылки: иначе deleteAll(users) других тестов упадёт по FK
        eventRepository.deleteAll();
        parcelRepository.deleteAll();
    }

    // --- Создание ---

    @Test
    void create_returnsPriceAndTrackingNumberAndFirstEvent() throws Exception {
        JsonNode parcel = createParcel(ownerToken);

        assertThat(parcel.get("trackingNumber").asText()).startsWith("CT-").hasSize(13);
        assertThat(parcel.get("status").asText()).isEqualTo("CREATED");
        assertThat(parcel.get("price").decimalValue()).isPositive();

        mockMvc.perform(get("/api/v1/parcels/" + parcel.get("id").asLong())
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].status").value("CREATED"));
    }

    @Test
    void create_sameWarehouses_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelBody(1, 1)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_byDispatcher_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", "Bearer " + dispatcherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelBody(1, 2)))
                .andExpect(status().isForbidden());
    }

    // --- Ownership: самый важный тест проекта (SDP, Фаза 2) ---

    @Test
    @DisplayName("Чужую посылку не видит USER (403), видят DISPATCHER и ADMIN")
    void ownershipMatrix() throws Exception {
        long parcelId = createParcel(ownerToken).get("id").asLong();
        String url = "/api/v1/parcels/" + parcelId;

        mockMvc.perform(get(url).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(url).header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url).header("Authorization", "Bearer " + dispatcherToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(url).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void myParcels_returnsOnlyOwn() throws Exception {
        createParcel(ownerToken);
        createParcel(ownerToken);
        createParcel(strangerToken);

        mockMvc.perform(get("/api/v1/parcels/my")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // --- Отмена ---

    @Test
    void cancel_byOwnerFromCreated_ok_thenSecondCancel409() throws Exception {
        long parcelId = createParcel(ownerToken).get("id").asLong();
        String url = "/api/v1/parcels/" + parcelId + "/cancel";

        mockMvc.perform(post(url).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post(url).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_byStranger_returns403() throws Exception {
        long parcelId = createParcel(ownerToken).get("id").asLong();

        mockMvc.perform(post("/api/v1/parcels/" + parcelId + "/cancel")
                        .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());
    }

    // --- Цена ---

    @Test
    void calculatePrice_matchesCreatedParcelPrice() throws Exception {
        String body = """
                {"originWarehouseId":1,"destinationWarehouseId":2,"weightKg":10,
                 "lengthCm":30,"widthCm":20,"heightCm":10}
                """;
        JsonNode quote = parse(mockMvc.perform(post("/api/v1/parcels/calculate-price")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());

        JsonNode parcel = createParcel(ownerToken);

        assertThat(quote.get("price").decimalValue())
                .isEqualByComparingTo(parcel.get("price").decimalValue());
    }

    // --- Публичный трекинг ---

    @Test
    @DisplayName("Аноним находит посылку по номеру, личные данные замаскированы")
    void publicTracking_anonymous_masked() throws Exception {
        JsonNode parcel = createParcel(ownerToken);
        String number = parcel.get("trackingNumber").asText();

        mockMvc.perform(get("/api/v1/tracking/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value(number))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.recipientNameMasked").value("П***"))
                .andExpect(jsonPath("$.recipientName").doesNotExist())
                .andExpect(jsonPath("$.recipientPhone").doesNotExist())
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void publicTracking_unknownNumber_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/CT-UNKNOWN404"))
                .andExpect(status().isNotFound());
    }

    // --- Хелперы ---

    private String createUserAndLogin(String email, Role role) throws Exception {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Тест")
                .lastName("Тестов")
                .role(role)
                .build());
        JsonNode auth = parse(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn());
        return auth.get("accessToken").asText();
    }

    private JsonNode createParcel(String token) throws Exception {
        return parse(mockMvc.perform(post("/api/v1/parcels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(parcelBody(1, 2)))
                .andExpect(status().isCreated()).andReturn());
    }

    private String parcelBody(long originId, long destinationId) {
        return """
                {"originWarehouseId":%d,"destinationWarehouseId":%d,
                 "recipientName":"Пётр Получателев","recipientPhone":"+420777888999",
                 "recipientEmail":"petr@test.io","weightKg":10,
                 "lengthCm":30,"widthCm":20,"heightCm":10,"declaredValue":100}
                """.formatted(originId, destinationId);
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
