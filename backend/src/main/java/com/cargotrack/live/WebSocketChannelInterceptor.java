package com.cargotrack.live;

import com.cargotrack.auth.JwtService;
import com.cargotrack.auth.UserPrincipal;
import com.cargotrack.user.UserRepository;
import com.cargotrack.user.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final WebSocketSubscriptionAuthorizer authorizer;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor);
        } else if (accessor.getCommand() == StompCommand.SEND) {
            throw new AccessDeniedException("Client messages are not supported");
        } else if (accessor.getCommand() == StompCommand.DISCONNECT) {
            sessionRegistry.unregister(accessor.getSessionId());
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BadCredentialsException("Bearer token is required");
        }
        try {
            Claims claims = jwtService.parse(header.substring(BEARER_PREFIX.length()));
            Long userId = jwtService.extractUserId(claims);
            UserPrincipal principal = userRepository.findById(userId)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .map(UserPrincipal::from)
                    .orElseThrow(() -> new BadCredentialsException("User is unavailable"));
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            accessor.setUser(authentication);
            sessionRegistry.register(accessor.getSessionId(), authentication);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BadCredentialsException("Invalid or expired token", exception);
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        UserPrincipal principal = currentPrincipal(accessor);
        if (!authorizer.canSubscribe(principal, accessor.getDestination())) {
            throw new AccessDeniedException("Subscription is not allowed");
        }
    }

    private UserPrincipal currentPrincipal(StompHeaderAccessor accessor) {
        UserPrincipal sessionPrincipal = accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserPrincipal userPrincipal
                ? userPrincipal
                : null;
        if (sessionPrincipal == null) {
            return null;
        }
        UserPrincipal currentPrincipal = userRepository.findById(sessionPrincipal.getId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(UserPrincipal::from)
                .orElseThrow(() -> new BadCredentialsException("User is unavailable"));
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                currentPrincipal, null, currentPrincipal.getAuthorities()));
        return currentPrincipal;
    }
}
