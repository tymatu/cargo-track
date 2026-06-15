package com.cargotrack.auth;

import com.cargotrack.audit.AuditAction;
import com.cargotrack.audit.Auditable;
import com.cargotrack.audit.AuthAuditEvent;
import com.cargotrack.auth.dto.AuthTokens;
import com.cargotrack.auth.dto.LoginRequest;
import com.cargotrack.auth.dto.RegisterRequest;
import com.cargotrack.common.ApiException;
import com.cargotrack.common.EmailNormalizer;
import com.cargotrack.config.JwtProperties;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserDto;
import com.cargotrack.user.UserMapper;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String BAD_CREDENTIALS = "Неверный email или пароль";
    private static final String INVALID_REFRESH = "Недействительный refresh-токен";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Auditable(action = AuditAction.USER_REGISTERED, entityType = "User", actorFromResult = true)
    public UserDto register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email уже зарегистрирован");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(Role.USER)
                .build();
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.email());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (AuthenticationException e) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.unauthorized(BAD_CREDENTIALS));
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthTokens refresh(String tokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> ApiException.unauthorized(INVALID_REFRESH));

        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            log.warn("Refresh-token reuse detected for user {}; revoked {} tokens",
                    stored.getUser().getId(), revoked);
            publishAuthAudit(AuditAction.SUSPICIOUS_REFRESH_REUSE, stored.getUser());
            throw ApiException.unauthorized(INVALID_REFRESH);
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }

        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized(INVALID_REFRESH);
        }

        stored.setRevoked(true);
        AuthTokens response = issueTokens(user);
        publishAuthAudit(AuditAction.TOKEN_REFRESHED, user);
        return response;
    }

    @Transactional
    public void logout(String tokenValue) {
        if (tokenValue != null && !tokenValue.isBlank()) {
            refreshTokenRepository.findByToken(tokenValue)
                    .ifPresent(token -> token.setRevoked(true));
        }
        eventPublisher.publishEvent(new AuthAuditEvent(AuditAction.LOGOUT, null, null));
    }

    private AuthTokens issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(randomToken())
                .expiresAt(Instant.now().plus(jwtProperties.refreshTtl()))
                .build());
        return new AuthTokens(accessToken, refreshToken.getToken(), userMapper.toDto(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private void publishAuthAudit(AuditAction action, User user) {
        eventPublisher.publishEvent(new AuthAuditEvent(action, user.getId(), user.getEmail()));
    }
}
