package com.cargotrack.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuthAuditListener {

    private final AuditService auditService;

    @EventListener
    public void onLoginSuccess(AuthenticationSuccessEvent event) {
        AuditContext context = AuditContext.capture(event.getAuthentication());
        auditService.record(context, AuditAction.LOGIN_SUCCESS, "Authentication",
                context.userId(), null, null);
    }

    @EventListener
    public void onLoginFailure(AbstractAuthenticationFailureEvent event) {
        AuditContext context = AuditContext.capture(event.getAuthentication());
        auditService.record(context, AuditAction.LOGIN_FAILED, "Authentication",
                null, null, null);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onAuthOperation(AuthAuditEvent event) {
        AuditContext context = AuditContext.capture();
        if (event.userId() != null || event.username() != null) {
            context = context.withActor(event.userId(), event.username());
        }
        auditService.record(context, event.action(), "Authentication",
                event.userId(), null, null);
    }
}
