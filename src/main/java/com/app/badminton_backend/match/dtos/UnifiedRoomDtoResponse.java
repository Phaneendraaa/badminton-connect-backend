package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchOrigin;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified room card returned by GET /match/my-rooms.
 *
 * Aggregates both CHALLENGE-origin matches (via MatchInvite) and
 * OPEN-origin matches (via MatchPlayer) into a single list, de-duplicated
 * by matchId server-side so the frontend never sees the same room twice.
 *
 * The origin badge ("Challenge" vs "Open Post") gives context on which
 * flow a given room came from — avoids the UX confusion of seeing a room
 * with no clear origin.
 */
@Data
@Builder
public class UnifiedRoomDtoResponse {

    private UUID matchId;
    private String matchName;
    private MatchType matchType;

    /**
     * Origin badge: CHALLENGE (friend-challenge flow) or OPEN (open-post flow).
     * Use this to label the card in the UI.
     */
    private MatchOrigin origin;

    private MatchStatus status;
    private LocalDateTime scheduledAt;
    private Integer slotsJoined;
    private Integer slotsTotal;
    private LocalDateTime createdAt;

    /**
     * UUID of the linked MatchPost, if origin == OPEN.
     * Null for CHALLENGE-origin rooms (they have no post).
     */
    private UUID postId;
}
