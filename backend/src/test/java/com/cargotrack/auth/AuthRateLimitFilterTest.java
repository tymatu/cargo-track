package com.cargotrack.auth;

import com.cargotrack.config.AuthRateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    @Test
    void thirdAuthRequestFromSameAddress_isRejected() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new AuthRateLimitProperties(
                        true, 2, 2, Duration.ofHours(1), Duration.ofMinutes(15)),
                new ObjectMapper());

        assertThat(execute(filter).getStatus()).isEqualTo(200);
        assertThat(execute(filter).getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = execute(filter);
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("3600");
        assertThat(rejected.getContentType()).startsWith("application/problem+json");
    }

    @Test
    void forwardedAddressFromTrustedProxy_getsSeparateBucket() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new AuthRateLimitProperties(
                        true, 1, 1, Duration.ofHours(1), Duration.ofMinutes(15)),
                new ObjectMapper());

        assertThat(execute(filter, "172.18.0.2", "203.0.113.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "172.18.0.2", "203.0.113.11").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "172.18.0.2", "203.0.113.10").getStatus()).isEqualTo(429);
    }

    @Test
    void forwardedAddressFromPublicRemote_isIgnored() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new AuthRateLimitProperties(
                        true, 1, 1, Duration.ofHours(1), Duration.ofMinutes(15)),
                new ObjectMapper());

        assertThat(execute(filter, "198.51.100.5", "203.0.113.10").getStatus()).isEqualTo(200);
        assertThat(execute(filter, "198.51.100.5", "203.0.113.11").getStatus()).isEqualTo(429);
    }

    @Test
    void logoutRequests_areNotRateLimited() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new AuthRateLimitProperties(
                        true, 1, 1, Duration.ofHours(1), Duration.ofMinutes(15)),
                new ObjectMapper());

        assertThat(executePath(filter, "/api/v1/auth/logout").getStatus()).isEqualTo(200);
        assertThat(executePath(filter, "/api/v1/auth/logout").getStatus()).isEqualTo(200);
    }

    @Test
    void emptyRefreshProbeWithoutCookie_doesNotConsumeAuthBucket() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(
                new AuthRateLimitProperties(
                        true, 1, 1, Duration.ofHours(1), Duration.ofMinutes(15)),
                new ObjectMapper());

        assertThat(executePath(filter, "/api/v1/auth/refresh", "{}").getStatus()).isEqualTo(200);
        assertThat(execute(filter).getStatus()).isEqualTo(200);
        assertThat(execute(filter).getStatus()).isEqualTo(429);
    }

    private MockHttpServletResponse execute(AuthRateLimitFilter filter) throws Exception {
        return executePath(filter, "/api/v1/auth/login");
    }

    private MockHttpServletResponse executePath(AuthRateLimitFilter filter, String uri) throws Exception {
        return executePath(filter, uri, null);
    }

    private MockHttpServletResponse executePath(
            AuthRateLimitFilter filter,
            String uri,
            String content) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("203.0.113.10");
        if (content != null) {
            request.setContent(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse execute(
            AuthRateLimitFilter filter,
            String remoteAddress,
            String forwardedFor) throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
