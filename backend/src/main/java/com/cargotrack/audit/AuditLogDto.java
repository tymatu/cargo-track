package com.cargotrack.audit;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record AuditLogDto(
        Long id,
        Long userId,
        String username,
        AuditAction action,
        String entityType,
        Long entityId,
        JsonNode oldValue,
        JsonNode newValue,
        String ipAddress,
        String userAgent,
        String httpMethod,
        String endpoint,
        Instant createdAt
) {

    static AuditLogDto from(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getUserId(),
                log.getUsername(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getHttpMethod(),
                log.getEndpoint(),
                log.getCreatedAt());
    }
}
