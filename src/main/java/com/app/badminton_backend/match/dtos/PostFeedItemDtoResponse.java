package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.PostStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight item shape for the paginated MatchFeed.
 * Heavier detail (roster, pending requests) lives in PostDetailDtoResponse.
 */
@Data
@Builder
public class PostFeedItemDtoResponse {

    private UUID postId;
    private UUID matchId;
    private MatchType matchType;
    private String location;
    private LocalDateTime scheduledAt;
    private Integer eloMin;
    private Integer eloMax;
    private Integer slotsTotal;
    private Integer slotsJoined;  // current number of filled slots (from companion Match)
    private PostStatus status;
    private LocalDateTime createdAt;

    // Organizer snapshot (enough for the feed card)
    private UUID organizerId;
    private String organizerName;
    private String organizerAvatarUrl;
    private Integer organizerElo;
}
