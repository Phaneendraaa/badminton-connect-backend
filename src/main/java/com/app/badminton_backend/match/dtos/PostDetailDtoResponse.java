package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.PostStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full post detail including organizer profile and confirmed roster.
 * Returned by GET /match-post/{id}.
 */
@Data
@Builder
public class PostDetailDtoResponse {

    private UUID postId;
    private UUID matchId;
    private MatchType matchType;
    private String location;
    private LocalDateTime scheduledAt;
    private Integer eloMin;
    private Integer eloMax;
    private Integer slotsTotal;
    private Integer slotsJoined;
    private PostStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    // Full organizer profile
    private UUID organizerId;
    private String organizerName;
    private String organizerAvatarUrl;
    private Integer organizerElo;

    /**
     * Confirmed players currently in the match.
     * Shape matches the UserSearchDtoResponse-style card used
     * in the friend-search flow (name, avatarUrl, eloRating).
     */
    private List<RosterPlayerDto> confirmedRoster;

    @Data
    @Builder
    public static class RosterPlayerDto {
        private UUID userId;
        private String name;
        private String profilePictureUrl;
        private Integer eloRating;
    }
}
