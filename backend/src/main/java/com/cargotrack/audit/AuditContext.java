package com.cargotrack.audit;

import com.cargotrack.auth.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public record AuditContext(
        Long userId,
        String username,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String endpoint
) {

    public static AuditContext capture() {
        return capture(SecurityContextHolder.getContext().getAuthentication());
    }

    public static AuditContext capture(Authentication authentication) {
        Long userId = null;
        String username = null;
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof UserPrincipal principal) {
                userId = principal.getId();
                username = principal.getUsername();
            } else if (!"anonymousUser".equals(authentication.getName())) {
                username = authentication.getName();
            }
        }

        HttpServletRequest request = currentRequest();
        return new AuditContext(
                userId,
                username,
                request == null ? null : truncate(request.getRemoteAddr(), 45),
                request == null ? null : request.getHeader("User-Agent"),
                request == null ? null : truncate(request.getMethod(), 10),
                request == null ? null : truncate(request.getRequestURI(), 255));
    }

    public AuditContext withActor(Long actorId, String actorUsername) {
        return new AuditContext(
                actorId,
                actorUsername,
                ipAddress,
                userAgent,
                httpMethod,
                endpoint);
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
