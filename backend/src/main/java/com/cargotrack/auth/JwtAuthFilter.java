package com.cargotrack.auth;

import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Достаёт Bearer-токен из заголовка, валидирует подпись и срок,
 * загружает пользователя и проверяет, что он не заблокирован,
 * затем кладёт Authentication в SecurityContext (SDP, раздел 5.3).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
            Long userId = jwtService.extractUserId(claims);

            userRepository.findById(userId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .map(UserPrincipal::from)
                    .ifPresent(principal -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (JwtException | IllegalArgumentException e) {
            // невалидный/истёкший токен: продолжаем без аутентификации,
            // на защищённых путях entry point вернёт 401
            log.debug("JWT rejected: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
