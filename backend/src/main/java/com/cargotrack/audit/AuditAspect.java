package com.cargotrack.audit;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void onSuccess(Auditable auditable, Object result) {
        Long entityId = AuditEntityIdResolver.resolveId(result);
        AuditContext context = AuditContext.capture();
        if (auditable.actorFromResult() && context.userId() == null) {
            context = context.withActor(entityId, AuditEntityIdResolver.resolveUsername(result));
        }

        AuditContext capturedContext = context;
        Runnable persist = () -> auditService.record(
                capturedContext,
                auditable.action(),
                auditable.entityType(),
                entityId,
                null,
                result);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    persist.run();
                }
            });
        } else {
            persist.run();
        }
    }
}
