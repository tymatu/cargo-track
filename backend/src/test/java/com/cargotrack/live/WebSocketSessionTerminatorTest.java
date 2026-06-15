package com.cargotrack.live;

import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketSessionTerminatorTest {

    @Test
    void disconnectUserSendsDisconnectForRegisteredSessions() {
        WebSocketSessionRegistry registry = new WebSocketSessionRegistry();
        MessageChannel channel = mock(MessageChannel.class);
        WebSocketSessionTerminator terminator = new WebSocketSessionTerminator(registry, channel);
        UserPrincipal principal = UserPrincipal.from(User.builder()
                .id(42L)
                .email("driver@test.io")
                .passwordHash("hash")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .warehouseId(1L)
                .build());
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        registry.register("session-a", authentication);
        registry.register("session-b", authentication);

        terminator.disconnectUser(42L);

        ArgumentCaptor<Message<?>> captor = ArgumentCaptor.forClass(Message.class);
        verify(channel, atLeastOnce()).send(captor.capture());
        List<String> disconnectedSessionIds = captor.getAllValues().stream()
                .map(StompHeaderAccessor::wrap)
                .filter(accessor -> accessor.getCommand() == StompCommand.DISCONNECT)
                .map(StompHeaderAccessor::getSessionId)
                .toList();
        assertThat(disconnectedSessionIds).containsExactlyInAnyOrder("session-a", "session-b");
        assertThat(registry.sessionsForUser(42L)).isEmpty();
    }
}
