package com.cargotrack.auth;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowTest {

    private static final String EMAIL = "ivan@test.io";
    private static final String PASSWORD = "secret-password-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @BeforeEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // --- Р РµРіРёСЃС‚СЂР°С†РёСЏ ---

    @Test
    void register_returns201AndUserRole() throws Exception {
        mockMvc.perform(register(EMAIL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        mockMvc.perform(register(EMAIL)).andExpect(status().isConflict());
    }

    @Test
    void register_invalidBody_returns400WithProblemDetail() throws Exception {
        String body = """
                {"email":"not-an-email","password":"123","firstName":"","lastName":""}
                """;
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    // --- Р’С…РѕРґ ---

    @Test
    void login_returnsTokensAndUser() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(EMAIL));
    }

    @Test
    @DisplayName("РќРµРІРµСЂРЅС‹Р№ РїР°СЂРѕР»СЊ Рё РЅРµСЃСѓС‰РµСЃС‚РІСѓСЋС‰РёР№ email РґР°СЋС‚ РѕРґРёРЅР°РєРѕРІС‹Р№ 401")
    void login_badCredentials_uniform401() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());

        MvcResult wrongPassword = mockMvc.perform(login(EMAIL, "wrong-password-1"))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownEmail = mockMvc.perform(login("ghost@test.io", PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(unknownEmail.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void login_blockedUser_returns401() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        blockUser(EMAIL);

        mockMvc.perform(login(EMAIL, PASSWORD)).andExpect(status().isUnauthorized());
    }

    // --- Р—Р°С‰РёС‰С‘РЅРЅС‹Рµ СЌРЅРґРїРѕРёРЅС‚С‹ ---

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void protectedEndpoint_withToken_returns200() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        JsonNode auth = loginAndParse();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    // --- Р РѕС‚Р°С†РёСЏ refresh ---

    @Test
    void refresh_rotatesToken() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        JsonNode auth = loginAndParse();
        String oldRefresh = auth.get("refreshToken").asText();

        JsonNode refreshed = parse(mockMvc.perform(refresh(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn());

        assertThat(refreshed.get("refreshToken").asText()).isNotEqualTo(oldRefresh);
    }

    @Test
    @DisplayName("Reuse РїРѕРіР°С€РµРЅРЅРѕРіРѕ refresh-С‚РѕРєРµРЅР° РѕС‚Р·С‹РІР°РµС‚ РІСЃРµ С‚РѕРєРµРЅС‹ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ")
    void refresh_reuseRevokedToken_revokesEverything() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        JsonNode auth = loginAndParse();
        String oldRefresh = auth.get("refreshToken").asText();

        JsonNode refreshed = parse(mockMvc.perform(refresh(oldRefresh))
                .andExpect(status().isOk()).andReturn());
        String newRefresh = refreshed.get("refreshToken").asText();

        // РїРѕРІС‚РѕСЂРЅРѕРµ РёСЃРїРѕР»СЊР·РѕРІР°РЅРёРµ СЃС‚Р°СЂРѕРіРѕ в†’ 401
        mockMvc.perform(refresh(oldRefresh)).andExpect(status().isUnauthorized());
        // Рё РЅРѕРІС‹Р№ С‚РѕР¶Рµ РѕС‚РѕР·РІР°РЅ (СЂРµР°РєС†РёСЏ РЅР° РєСЂР°Р¶Сѓ)
        mockMvc.perform(refresh(newRefresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_unknownToken_returns401() throws Exception {
        mockMvc.perform(refresh("unknown-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_blockedUser_returns401() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        JsonNode auth = loginAndParse();
        blockUser(EMAIL);

        mockMvc.perform(refresh(auth.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    // --- Logout ---

    @Test
    void logout_revokesRefreshToken() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        JsonNode auth = loginAndParse();
        String refreshToken = auth.get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + auth.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"whatever\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- РҐРµР»РїРµСЂС‹ ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(String email) {
        String body = """
                {"email":"%s","password":"%s","firstName":"РРІР°РЅ","lastName":"РўРµСЃС‚РѕРІ","phone":"+420111222333"}
                """.formatted(email, PASSWORD);
        return post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String email, String password) {
        String body = "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder refresh(String token) {
        return post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + token + "\"}");
    }

    private JsonNode loginAndParse() throws Exception {
        return parse(mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Transactional
    void blockUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }
}
