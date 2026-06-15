package com.cargotrack.auth;

import com.cargotrack.config.AuthRateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String LOGOUT_PATH = AUTH_PREFIX + "logout";
    private static final String REFRESH_PATH = AUTH_PREFIX + "refresh";
    private static final String REFRESH_COOKIE = "ct_refresh_token";

    private final AuthRateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger requestsSinceCleanup = new AtomicInteger();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith(AUTH_PREFIX)
                || LOGOUT_PATH.equals(request.getRequestURI())
                || isCookieLessEmptyRefresh(request);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        cleanupExpiredEntries();
        BucketEntry entry = buckets.compute(clientAddress(request), (key, current) -> {
            if (current == null) {
                return new BucketEntry(newBucket(), Instant.now());
            }
            current.lastSeen = Instant.now();
            return current;
        });
        if (entry.bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Слишком много запросов аутентификации. Повторите попытку позже");
        problem.setTitle("Too many requests");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After",
                Long.toString(Math.max(1, properties.refillPeriod().toSeconds())));
        objectMapper.writeValue(response.getWriter(), problem);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit
                        .capacity(properties.capacity())
                        .refillGreedy(properties.refillTokens(), properties.refillPeriod()))
                .build();
    }

    private void cleanupExpiredEntries() {
        if (requestsSinceCleanup.incrementAndGet() % 100 != 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(properties.bucketTtl());
        buckets.entrySet().removeIf(entry -> entry.getValue().lastSeen.isBefore(cutoff));
    }

    private String clientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!isTrustedProxyAddress(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstHop = forwardedFor.split(",", 2)[0].trim();
            if (!firstHop.isBlank()) {
                return firstHop;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? remoteAddress : realIp.trim();
    }

    private boolean isTrustedProxyAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            return inetAddress.isLoopbackAddress()
                    || inetAddress.isSiteLocalAddress()
                    || inetAddress.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private boolean isCookieLessEmptyRefresh(HttpServletRequest request) {
        if (!REFRESH_PATH.equals(request.getRequestURI()) || hasRefreshCookie(request)) {
            return false;
        }
        long contentLength = request.getContentLengthLong();
        return contentLength >= 0 && contentLength <= 2;
    }

    private boolean hasRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            String value = cookie.getValue();
            if (REFRESH_COOKIE.equals(cookie.getName()) && value != null && !value.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile Instant lastSeen;

        private BucketEntry(Bucket bucket, Instant lastSeen) {
            this.bucket = bucket;
            this.lastSeen = lastSeen;
        }
    }
}
