package com.app.badminton_backend.auth.config;

import com.app.badminton_backend.auth.util.AuthUtil;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
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

import java.security.Principal;
import java.util.UUID;

/**
 * Authenticates STOMP connections (CONNECT) and enforces per-topic authorization
 * on SUBSCRIBE for protected match-chat topics.
 *
 * == CONNECT ==
 * The client must send the JWT in the CONNECT frame header:
 *   connectHeaders: { Authorization: "Bearer <token>" }
 * On a valid token the accessor's user principal is set; Spring STOMP carries
 * it forward on all subsequent frames from that connection.
 *
 * == SUBSCRIBE ==
 * Any SUBSCRIBE to /topic/match/{matchId} is intercepted.  The subscriber must
 * be an active MatchPlayer OR the match organizer.  If not, the message is
 * dropped (returns null) — Spring STOMP sends an ERROR frame back to the client
 * and terminates the subscription.
 *
 * Other topics (/topic/post/..., /topic/match-play/...) are NOT guarded here;
 * they rely on the REST-level checks their respective services already enforce.
 */
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final AuthUtil authUtil;
    private final UserDetailsService userDetailsService;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchRepository matchRepository;

    /** Prefix for the confirmed-only match chat topic. */
    private static final String MATCH_CHAT_TOPIC_PREFIX = "/topic/match/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // ── CONNECT: authenticate via JWT ──────────────────────────────────────
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
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
            return message;
        }

        // ── SUBSCRIBE: enforce participant-only access to /topic/match/{matchId} ─
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith(MATCH_CHAT_TOPIC_PREFIX)) {
                // Extract matchId from destination: /topic/match/<uuid>
                String suffix = destination.substring(MATCH_CHAT_TOPIC_PREFIX.length());
                // Guard: suffix must be a valid UUID (rule out /topic/match-play/... which
                // starts with "match/" only if someone uses a partial prefix — they won't,
                // but be safe and reject non-UUID suffixes silently).
                UUID matchId;
                try {
                    matchId = UUID.fromString(suffix);
                } catch (IllegalArgumentException ex) {
                    // Not a valid UUID — not a match-chat topic, let it through
                    return message;
                }

                // Resolve the subscriber's userId from the STOMP principal
                UUID subscriberUserId = resolveUserId(accessor.getUser());
                if (subscriberUserId == null) {
                    // No authenticated principal — reject
                    return null;
                }

                // Is this user an active MatchPlayer for this match?
                boolean isPlayer = matchPlayerRepository
                        .findByMatchId(matchId)
                        .stream()
                        .anyMatch(mp -> mp.getUserId().equals(subscriberUserId));

                if (isPlayer) return message;

                // Is this user the organizer?
                boolean isOrganizer = matchRepository.findById(matchId)
                        .map(m -> m.getOrganizerId().equals(subscriberUserId))
                        .orElse(false);

                if (isOrganizer) return message;

                // Not a participant — drop the SUBSCRIBE frame.
                // Spring STOMP responds to a null return with a STOMP ERROR frame.
                return null;
            }
        }

        return message;
    }

    /**
     * Extracts the userId from a STOMP Principal.
     *
     * The Principal is a UsernamePasswordAuthenticationToken whose name is the
     * phone number (set during CONNECT). UserDetailsService returns CustomUserDetails
     * which wraps the User entity — we unwrap it to get the UUID.
     * Returns null if the principal is absent or the user cannot be found.
     */
    private UUID resolveUserId(Principal principal) {
        if (principal == null) return null;
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(principal.getName());
            if (userDetails instanceof com.app.badminton_backend.auth.entity.CustomUserDetails cud) {
                return cud.getUser().getId();
            }
        } catch (Exception ignored) {
            // Cannot resolve — deny
        }
        return null;
    }
}
