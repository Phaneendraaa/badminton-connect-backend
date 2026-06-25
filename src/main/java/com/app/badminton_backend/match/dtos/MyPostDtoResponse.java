package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.PostStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response shape for GET /match-post/mine — the organizer's own posts.
 * Includes pendingRequestCount so the UI can show an unread badge
 * without a separate join-request fetch per post.
 */
@Data
@Builder
public class MyPostDtoResponse {

    private UUID postId;
    private UUID matchId;
    private String title;
    private MatchType matchType;
    private String city;
    private String cityOther;
    private String location;
    private LocalDateTime scheduledAt;
    private Integer eloMin;
    private Integer eloMax;
    private Integer slotsTotal;
    private Integer slotsJoined;
    private PostStatus status;
    private LocalDateTime createdAt;

    /**
     * Number of PENDING join requests currently awaiting organizer review.
     * Computed server-side so the frontend gets a ready-to-display number
     * without any extra round-trips.
     */
    private Integer pendingRequestCount;
}
