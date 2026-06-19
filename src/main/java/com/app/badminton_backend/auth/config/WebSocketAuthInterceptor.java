package com.app.badminton_backend.auth.config;

import com.app.badminton_backend.auth.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP connections using the same JWT mechanism as JwtAuthFilter.
 *
 * The client must send the JWT in the CONNECT frame header:
 *   connectHeaders: { Authorization: "Bearer <token>" }
 *
 * On a valid token the StompHeaderAccessor's user principal is set, which Spring
 * STOMP then carries forward on all subsequent frames from that connection.
 * On an invalid/missing token the CONNECT frame is passed through unauthenticated
 * — the MatchChatService auth check (MatchPlayer lookup) will then reject sends/reads.
 *
 * This mirrors exactly what JwtAuthFilter does for REST requests.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final AuthUtil authUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                if (authUtil.isTokenValid(token)) {
                    String phoneNumber = authUtil.getPhoneNumberFromToken(token);
                    if (phoneNumber != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(phoneNumber);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                    }
                }
            }
        }

        return message;
    }
}
