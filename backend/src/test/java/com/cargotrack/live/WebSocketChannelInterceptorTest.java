package com.cargotrack.live;

import com.cargotrack.auth.JwtService;
import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.config.JwtProperties;
import com.cargotrack.user.Role;
import com.cargotrack.user.User;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WebSocketChannelInterceptorTest {

    private UserRepository userRepository;
    private JwtService jwtService;
    private WebSocketSubscriptionAuthorizer authorizer;
    private WebSocketSessionRegistry sessionRegistry;
    private WebSocketChannelInterceptor interceptor;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        jwtService = new JwtService(new JwtProperties(
                "websocket-test-secret-0123456789-abcdefghijklmnopqrstuvwxyz",
                Duration.ofMinutes(15),
                Duration.ofDays(14)));
        authorizer = mock(WebSocketSubscriptionAuthorizer.class);
        sessionRegistry = mock(WebSocketSessionRegistry.class);
        interceptor = new WebSocketChannelInterceptor(
                jwtService, userRepository, authorizer, sessionRegistry);
        user = User.builder()
                .id(10L)
                .email("driver@test.io")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("Driver")
                .role(Role.DRIVER)
                .status(UserStatus.ACTIVE)
                .warehouseId(1L)
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
    }

    @Test
    void connectWithBearerToken_setsAuthenticatedUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(
                "Authorization", "Bearer " + jwtService.generateAccessToken(user));
        accessor.setSessionId("ws-session-1");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);

        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo(user.getEmail());
        verify(sessionRegistry).register(eq("ws-session-1"), any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void connectWithoutToken_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(
                message, mock(org.springframework.messaging.MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void subscribeAfterUserIsBlocked_isRejectedBeforeAuthorizer() {
        UserPrincipal stalePrincipal = UserPrincipal.from(user);
        user.setStatus(UserStatus.BLOCKED);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/trucks/77/position");
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                stalePrincipal, null, stalePrincipal.getAuthorities()));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, mock(MessageChannel.class)))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(authorizer);
    }

    @Test
    void disconnect_unregistersSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId("ws-session-2");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        interceptor.preSend(message, mock(MessageChannel.class));

        verify(sessionRegistry).unregister("ws-session-2");
    }
}
