package com.cargotrack.auth;

import com.cargotrack.TestcontainersConfiguration;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
    private static final String REFRESH_COOKIE = "ct_refresh_token";
    private static final String SESSION_COOKIE = "ct_session";

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

    // --- Registration ---

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
    void register_normalizesEmailCase() throws Exception {
        mockMvc.perform(register("IVAN@Test.IO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(register(EMAIL)).andExpect(status().isConflict());
        mockMvc.perform(login("IVAN@Test.IO", PASSWORD)).andExpect(status().isOk());
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

    // --- Login ---

    @Test
    void login_returnsTokensAndUser() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn();

        assertAuthCookiesIssued(result);
    }

    @Test
    @DisplayName("Wrong password and unknown email return the same 401 response")
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

    // --- Protected endpoints ---

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

    // --- Refresh rotation ---

    @Test
    void refresh_rotatesToken() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        MvcResult auth = loginAndReturn();
        String oldRefresh = refreshTokenFrom(auth);

        MvcResult refreshed = mockMvc.perform(refresh(oldRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertThat(refreshTokenFrom(refreshed)).isNotEqualTo(oldRefresh);
    }

    @Test
    void refresh_acceptsHttpOnlyCookie() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        MvcResult auth = loginAndReturn();
        String oldRefresh = refreshTokenFrom(auth);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(REFRESH_COOKIE, oldRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertAuthCookiesIssued(refreshed);
        assertThat(refreshTokenFrom(refreshed)).isNotEqualTo(oldRefresh);
    }

    @Test
    @DisplayName("Reusing a revoked refresh token revokes all user tokens")
    void refresh_reuseRevokedToken_revokesEverything() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        MvcResult auth = loginAndReturn();
        String oldRefresh = refreshTokenFrom(auth);

        MvcResult refreshed = mockMvc.perform(refresh(oldRefresh))
                .andExpect(status().isOk()).andReturn();
        String newRefresh = refreshTokenFrom(refreshed);

        // Reusing the old refresh token returns 401.
        mockMvc.perform(refresh(oldRefresh)).andExpect(status().isUnauthorized());
        // The new token is revoked too as a theft-reuse response.
        mockMvc.perform(refresh(newRefresh)).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_unknownToken_returns401() throws Exception {
        mockMvc.perform(refresh("unknown-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_blockedUser_returns401() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        MvcResult auth = loginAndReturn();
        blockUser(EMAIL);

        mockMvc.perform(refresh(refreshTokenFrom(auth)))
                .andExpect(status().isUnauthorized());
    }

    // --- Logout ---

    @Test
    void logout_revokesRefreshToken() throws Exception {
        mockMvc.perform(register(EMAIL)).andExpect(status().isCreated());
        MvcResult auth = loginAndReturn();
        JsonNode body = parse(auth);
        String refreshToken = refreshTokenFrom(auth);

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + body.get("accessToken").asText())
                        .cookie(new Cookie(REFRESH_COOKIE, refreshToken)))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .hasSize(2)
                .allMatch(value -> value.contains("Max-Age=0"));

        mockMvc.perform(refresh(refreshToken)).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_withoutToken_clearsCookies() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"whatever\"}"))
                .andExpect(status().isNoContent());
    }

    // --- Helpers ---

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder register(String email) {
        String body = """
                {"email":"%s","password":"%s","firstName":"Ivan","lastName":"Testov","phone":"+420111222333"}
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
        return parse(loginAndReturn());
    }

    private MvcResult loginAndReturn() throws Exception {
        return mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk()).andReturn();
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String refreshTokenFrom(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private void assertAuthCookiesIssued(MvcResult result) {
        Cookie refreshCookie = result.getResponse().getCookie(REFRESH_COOKIE);
        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isFalse();
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                .anyMatch(value -> value.contains(REFRESH_COOKIE + "=") && value.contains("HttpOnly"))
                .anyMatch(value -> value.contains(SESSION_COOKIE + "=1"));
    }

    @Transactional
    void blockUser(String email) {
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setStatus(UserStatus.BLOCKED);
        userRepository.save(user);
    }
}
