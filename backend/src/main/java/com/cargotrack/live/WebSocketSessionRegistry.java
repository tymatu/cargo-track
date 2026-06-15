package com.cargotrack.live;

import com.cargotrack.auth.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSessionRegistration> bySessionId = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> byUserId = new ConcurrentHashMap<>();

    public void register(String sessionId, Principal principal) {
        Long userId = userId(principal);
        if (sessionId == null || userId == null) {
            return;
        }
        unregister(sessionId);
        bySessionId.put(sessionId, new WebSocketSessionRegistration(sessionId, userId, principal));
        byUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }
        WebSocketSessionRegistration registration = bySessionId.remove(sessionId);
        if (registration == null) {
            return;
        }
        Set<String> sessions = byUserId.get(registration.userId());
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                byUserId.remove(registration.userId(), sessions);
            }
        }
    }

    public List<WebSocketSessionRegistration> sessionsForUser(Long userId) {
        Set<String> sessionIds = byUserId.get(userId);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionIds.stream()
                .map(bySessionId::get)
                .filter(registration -> registration != null)
                .toList();
    }

    private Long userId(Principal principal) {
        return principal instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserPrincipal userPrincipal
                ? userPrincipal.getId()
                : null;
    }
}
