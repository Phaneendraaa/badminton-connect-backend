package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.JoinRequestStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.PostStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response shape for a single join request.
 * Used by both "my requests" and "requests for my posts" endpoints.
 */
@Data
@Builder
public class JoinRequestDtoResponse {

    private UUID requestId;
    private UUID postId;
    private UUID matchId;

    // Requester info
    private UUID userId;
    private String userName;
    private String userAvatarUrl;
    private Integer eloAtRequest;

    private JoinRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    // Post context (useful for "my requests" view so the user knows which post)
    private String postLocation;
    private LocalDateTime postScheduledAt;
    private MatchType postMatchType;
    private PostStatus postStatus;
}
