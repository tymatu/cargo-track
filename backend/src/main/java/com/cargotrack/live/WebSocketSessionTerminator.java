package com.cargotrack.live;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WebSocketSessionTerminator {

    private final WebSocketSessionRegistry sessionRegistry;
    private final MessageChannel clientInboundChannel;

    public WebSocketSessionTerminator(
            WebSocketSessionRegistry sessionRegistry,
            @Lazy @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel) {
        this.sessionRegistry = sessionRegistry;
        this.clientInboundChannel = clientInboundChannel;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateUserSessions(UserWebSocketSessionsInvalidatedEvent event) {
        disconnectUser(event.userId());
    }

    public void disconnectUser(Long userId) {
        for (WebSocketSessionRegistration session : sessionRegistry.sessionsForUser(userId)) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
            accessor.setSessionId(session.sessionId());
            accessor.setUser(session.principal());
            accessor.setLeaveMutable(true);
            clientInboundChannel.send(
                    MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
            sessionRegistry.unregister(session.sessionId());
        }
    }
}
