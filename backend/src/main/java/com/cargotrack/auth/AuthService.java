package com.cargotrack.auth;

import com.cargotrack.auth.dto.AuthResponse;
import com.cargotrack.auth.dto.LoginRequest;
import com.cargotrack.auth.dto.RegisterRequest;
import com.cargotrack.common.ApiException;
import com.cargotrack.config.JwtProperties;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserDto;
import com.cargotrack.user.UserMapper;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email уже зарегистрирован");
        }
        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(Role.USER) // самостоятельная регистрация — всегда USER; сотрудников создаёт админ
                .build();
        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            // единое сообщение: не раскрываем, что именно неверно (SDP, чек-лист 5.7)
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> ApiException.unauthorized(BAD_CREDENTIALS));
        return issueTokens(user);
    }

    /**
     * Ротация refresh-токена (SDP, раздел 5.2): старый гасится, выдаётся новый.
     * Повторное использование погашенного токена — признак кражи:
     * отзываем все токены пользователя.
     *
     * <p>noRollbackFor: при reuse мы сначала отзываем токены и только потом
     * бросаем 401 — откат транзакции отменил бы отзыв и сломал защиту.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String tokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> ApiException.unauthorized(INVALID_REFRESH));

        if (stored.isRevoked()) {
            int revoked = refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            // TODO Фаза 3: событие SUSPICIOUS_REFRESH_REUSE в audit_log
            log.warn("Refresh-token reuse detected for user {}; revoked {} tokens",
                    stored.getUser().getId(), revoked);
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
        return issueTokens(user);
    }

    @Transactional
    public void logout(String tokenValue) {
        refreshTokenRepository.findByToken(tokenValue)
                .ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token(randomToken())
                .expiresAt(Instant.now().plus(jwtProperties.refreshTtl()))
                .build());
        return new AuthResponse(accessToken, refreshToken.getToken(), userMapper.toDto(user));
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
