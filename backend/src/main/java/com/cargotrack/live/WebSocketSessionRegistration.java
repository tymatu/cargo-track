package com.cargotrack.live;

import java.security.Principal;

public record WebSocketSessionRegistration(
        String sessionId,
        Long userId,
        Principal principal
) {
}
