package com.cargotrack.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    Optional<AuditLog> findFirstByActionAndEntityIdOrderByCreatedAtDesc(
            AuditAction action, Long entityId);

    Optional<AuditLog> findFirstByActionAndUsernameOrderByCreatedAtDesc(
            AuditAction action, String username);
}
