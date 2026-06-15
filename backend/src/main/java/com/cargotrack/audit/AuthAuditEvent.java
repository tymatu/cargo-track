package com.cargotrack.audit;

public record AuthAuditEvent(
        AuditAction action,
        Long userId,
        String username
) {
}
