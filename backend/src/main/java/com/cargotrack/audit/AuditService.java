package com.cargotrack.audit;

import com.cargotrack.common.PageResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async("auditExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditContext context, AuditAction action, String entityType,
                       Long entityId, Object oldValue, Object newValue) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .userId(context.userId())
                    .username(context.username())
                    .action(action)
                    .entityType(blankToNull(entityType))
                    .entityId(entityId)
                    .oldValue(toJson(oldValue))
                    .newValue(toJson(newValue))
                    .ipAddress(context.ipAddress())
                    .userAgent(context.userAgent())
                    .httpMethod(context.httpMethod())
                    .endpoint(context.endpoint())
                    .build());
        } catch (RuntimeException e) {
            log.error("Could not persist audit action {}", action, e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(Long userId, AuditAction action, String entityType,
                                           Long entityId, Instant from, Instant to, Pageable pageable) {
        Specification<AuditLog> specification = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (action != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (entityType != null && !entityType.isBlank()) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (from != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }

        return PageResponse.of(auditLogRepository.findAll(specification, pageable).map(AuditLogDto::from));
    }

    private JsonNode toJson(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
