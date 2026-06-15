package com.cargotrack.auth;

import com.cargotrack.auth.dto.AuthResponse;
import com.cargotrack.auth.dto.AuthTokens;
import com.cargotrack.auth.dto.LoginRequest;
import com.cargotrack.auth.dto.RefreshRequest;
import com.cargotrack.auth.dto.RegisterRequest;
import com.cargotrack.common.ApiException;
import com.cargotrack.config.JwtProperties;
import com.cargotrack.user.UserDto;
import com.cargotrack.user.UserMapper;
import com.cargotrack.user.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, JWT login, refresh rotation and logout")
public class AuthController {

    private static final String REFRESH_COOKIE = "ct_refresh_token";
    private static final String SESSION_COOKIE = "ct_session";
    private static final String INVALID_REFRESH = "Недействительный refresh-токен";

    private final AuthService authService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<UserDto> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthTokens response = authService.login(request);
        return withRefreshCookie(response, servletRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            HttpServletRequest servletRequest) {
        AuthTokens response = authService.refresh(resolveRefreshToken(request, cookieToken));
        return withRefreshCookie(response, servletRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            HttpServletRequest servletRequest) {
        authService.logout(resolveOptionalRefreshToken(request, cookieToken));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,
                        clearRefreshCookie(servletRequest).toString(),
                        clearSessionCookie(servletRequest).toString())
                .build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .map(userMapper::toDto)
                .orElseThrow(() -> ApiException.notFound("Пользователь не найден"));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(
            AuthTokens response,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookie(response.refreshToken(), servletRequest).toString(),
                        sessionCookie(servletRequest).toString())
                .body(response.toResponse());
    }

    private String resolveRefreshToken(RefreshRequest request, String cookieToken) {
        String token = resolveOptionalRefreshToken(request, cookieToken);
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        return token;
    }

    private String resolveOptionalRefreshToken(RefreshRequest request, String cookieToken) {
        if (cookieToken != null && !cookieToken.isBlank()) {
            return cookieToken;
        }
        return request == null ? null : request.refreshToken();
    }

    private ResponseCookie refreshCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(jwtProperties.refreshTtl())
                .build();
    }

    private ResponseCookie clearRefreshCookie(HttpServletRequest request) {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }

    private ResponseCookie sessionCookie(HttpServletRequest request) {
        return ResponseCookie.from(SESSION_COOKIE, "1")
                .httpOnly(false)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(jwtProperties.refreshTtl())
                .build();
    }

    private ResponseCookie clearSessionCookie(HttpServletRequest request) {
        return ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(false)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
