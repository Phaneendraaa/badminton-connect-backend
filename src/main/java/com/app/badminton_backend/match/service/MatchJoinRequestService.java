package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.exceptions.*;
import com.app.badminton_backend.match.dtos.JoinRequestDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchJoinRequest;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.enums.*;
import com.app.badminton_backend.match.repository.*;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchJoinRequestService {

    private final CurrentUserService currentUserService;
    private final MatchPostRepository matchPostRepository;
    private final MatchRepository matchRepository;
    private final MatchJoinRequestRepository matchJoinRequestRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final EloService eloService;
    private final ProfileRepository profileRepository;
    private final EntityManager entityManager;

    // -------------------------------------------------------------------------
    // REQUEST TO JOIN
    // -------------------------------------------------------------------------

    /**
     * A user requests to join an open post.
     *
     * Rejects explicitly (with distinct messages) for:
     *  - Self-join (user IS the post creator) — IDs compared with .equals(), not ==.
     *  - Post not OPEN.
     *  - scheduledAt has passed.
     *  - Duplicate PENDING request from the same user for the same post.
     */
    @Transactional
    public MatchJoinRequest requestToJoin(UUID postId) {
        UUID userId = currentUserService.getCurrentUser().getId();

        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        // Self-join check — use .equals(), never ==
        if (post.getCreatorId() == null || !profileRepository.existsById(post.getCreatorId())) {
            throw new IllegalStateException("Invalid organizer link: The creator of this post could not be found.");
        }

        if (post.getCreatorId().equals(userId)) {
            throw new IllegalArgumentException("You cannot request to join your own post");
        }

        if (post.getStatus() != PostStatus.OPEN) {
            throw new PostNotOpenException("Post is not open for joining (status: " + post.getStatus() + ")");
        }

        if (post.getScheduledAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("This post's scheduled time has already passed");
        }

        // Duplicate PENDING request check
        matchJoinRequestRepository.findByPostIdAndUserId(postId, userId).ifPresent(existing -> {
            if (existing.getStatus() == JoinRequestStatus.PENDING) {
                throw new DuplicateException("You already have a pending request for this post");
            }
            if (existing.getStatus() == JoinRequestStatus.ACCEPTED) {
                throw new DuplicateException("You have already been accepted into this match");
            }
            // REJECTED / CANCELLED: allow a fresh request by falling through
        });

        int eloAtRequest = eloService.getOrCreate(userId).getElo();

        return matchJoinRequestRepository.save(MatchJoinRequest.builder()
                .postId(postId)
                .matchId(post.getMatchId())
                .userId(userId)
                .status(JoinRequestStatus.PENDING)
                .eloAtRequest(eloAtRequest)
                .build());
    }

    // -------------------------------------------------------------------------
    // ACCEPT REQUEST (concurrency-safe)
    // -------------------------------------------------------------------------

    /**
     * Organizer accepts a join request.
     *
     * Concurrency design (see implementation_plan.md Phase 3):
     *  We use PESSIMISTIC_WRITE on the Match row to serialize concurrent accepts.
     *  This means two racing accepts for the same match will queue at the DB lock
     *  rather than both succeeding. The second one re-reads slotsJoined after
     *  the first transaction commits and sees the match is now full → MatchFullException.
     *
     *  This is intentional: a clean 409 is better UX than an optimistic-lock
     *  StaleObjectStateException that requires the caller to retry.
     *
     * Side effects on fill:
     *  - match.status → CREATED
     *  - post.status  → FULL
     *  - All remaining PENDING requests for the post are auto-rejected in one bulk UPDATE.
     */
    @Transactional
    public void acceptRequest(UUID requestId) {
        UUID organizerId = currentUserService.getCurrentUser().getId();

        MatchJoinRequest request = matchJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new PostNotFoundException("Join request not found: " + requestId));

        MatchPost post = matchPostRepository.findById(request.getPostId())
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + request.getPostId()));

        // Organizer-only guard — use .equals(), never ==
        if (!post.getCreatorId().equals(organizerId)) {
            throw new UnauthorizedActionException("Only the post creator can accept join requests");
        }

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("Request is not pending (current status: " + request.getStatus() + ")");
        }

        // Acquire a PESSIMISTIC_WRITE lock on the Match row.
        // This serializes concurrent accepts: the second transaction blocks here
        // until the first one commits, then re-reads the (now-incremented) slotsJoined.
        Match match = entityManager.find(Match.class, request.getMatchId(), LockModeType.PESSIMISTIC_WRITE);
        if (match == null) {
            throw new PostNotFoundException("Companion match not found: " + request.getMatchId());
        }

        // Re-check AFTER acquiring the lock — this is the critical re-read that
        // prevents the race condition where two accepts both read slotsJoined=3
        // on a 4-slot match and both increment to 4.
        if (match.getSlotsJoined() >= match.getSlotsTotal()) {
            throw new MatchFullException("All slots are already filled — cannot accept more players");
        }

        // Accept the request
        request.setStatus(JoinRequestStatus.ACCEPTED);
        request.setRespondedAt(LocalDateTime.now());
        matchJoinRequestRepository.save(request);

        // Add the player to the match. Auto-assign team based on join order:
        // first joiner → TEAM_A, second → TEAM_B, third → TEAM_A, fourth → TEAM_B.
        int teamACount = matchPlayerRepository.findByMatchIdAndTeam(match.getId(), Team.TEAM_A).size();
        int teamBCount = matchPlayerRepository.findByMatchIdAndTeam(match.getId(), Team.TEAM_B).size();
        Team assignedTeam = (teamACount <= teamBCount) ? Team.TEAM_A : Team.TEAM_B;

        int eloBefore = eloService.getOrCreate(request.getUserId()).getElo();
        matchPlayerRepository.save(MatchPlayer.builder()
                .matchId(match.getId())
                .userId(request.getUserId())
                .team(assignedTeam)
                .eloBefore(eloBefore)
                .build());

        // Increment slot count
        match.setSlotsJoined(match.getSlotsJoined() + 1);
        matchRepository.save(match);

        // Check if now full
        if (match.getSlotsJoined().equals(match.getSlotsTotal())) {
            match.setStatus(MatchStatus.CREATED);
            matchRepository.save(match);

            post.setStatus(PostStatus.FULL);
            matchPostRepository.save(post);

            // Auto-reject all remaining PENDING requests (single bulk UPDATE in one transaction).
            // This prevents dangling PENDING rows from sitting around after the post fills.
            matchJoinRequestRepository.rejectAllPendingForPost(post.getId());
        }
    }

    // -------------------------------------------------------------------------
    // REJECT REQUEST
    // -------------------------------------------------------------------------

    /** Organizer rejects a PENDING request. */
    @Transactional
    public void rejectRequest(UUID requestId) {
        UUID organizerId = currentUserService.getCurrentUser().getId();

        MatchJoinRequest request = matchJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new PostNotFoundException("Join request not found: " + requestId));

        MatchPost post = matchPostRepository.findById(request.getPostId())
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + request.getPostId()));

        if (!post.getCreatorId().equals(organizerId)) {
            throw new UnauthorizedActionException("Only the post creator can reject join requests");
        }

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be rejected");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setRespondedAt(LocalDateTime.now());
        matchJoinRequestRepository.save(request);
    }

    // -------------------------------------------------------------------------
    // CANCEL REQUEST (requester withdraws)
    // -------------------------------------------------------------------------

    /** Requester cancels their own PENDING request. */
    @Transactional
    public void cancelRequest(UUID requestId) {
        UUID userId = currentUserService.getCurrentUser().getId();

        MatchJoinRequest request = matchJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new PostNotFoundException("Join request not found: " + requestId));

        // Ownership check — use .equals(), never ==
        if (!request.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You can only cancel your own join requests");
        }

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new IllegalStateException("Only PENDING requests can be cancelled (current: " + request.getStatus() + ")");
        }

        request.setStatus(JoinRequestStatus.CANCELLED);
        request.setRespondedAt(LocalDateTime.now());
        matchJoinRequestRepository.save(request);
    }

    // -------------------------------------------------------------------------
    // LEAVE MATCH (accepted joiner leaves before match starts)
    // -------------------------------------------------------------------------

    /**
     * An ACCEPTED joiner leaves the match before it starts.
     *
     * This is distinct from "cancel my pending request":
     *  - Decrements Match.slotsJoined.
     *  - Deletes the MatchPlayer row so the player is no longer on a team.
     *  - If the post was FULL, sets it back to OPEN so it can accept new joiners.
     *  - Does NOT allow leaving once the match status is PLAYING or COMPLETED.
     */
    @Transactional
    public void leaveMatch(UUID requestId) {
        UUID userId = currentUserService.getCurrentUser().getId();

        MatchJoinRequest request = matchJoinRequestRepository.findById(requestId)
                .orElseThrow(() -> new PostNotFoundException("Join request not found: " + requestId));

        if (!request.getUserId().equals(userId)) {
            throw new UnauthorizedActionException("You can only leave a match you joined");
        }

        if (request.getStatus() != JoinRequestStatus.ACCEPTED) {
            throw new IllegalStateException("Only ACCEPTED requests support the leave action");
        }

        Match match = matchRepository.findById(request.getMatchId())
                .orElseThrow(() -> new PostNotFoundException("Match not found: " + request.getMatchId()));

        if (match.getStatus() == MatchStatus.PLAYING || match.getStatus() == MatchStatus.COMPLETED) {
            throw new IllegalStateException("Cannot leave a match that is already underway or completed");
        }

        // Delete the MatchPlayer row
        List<MatchPlayer> playerRows = matchPlayerRepository.findByMatchId(match.getId())
                .stream()
                .filter(mp -> mp.getUserId().equals(userId))
                .collect(Collectors.toList());
        matchPlayerRepository.deleteAll(playerRows);

        // Decrement slots
        match.setSlotsJoined(Math.max(1, match.getSlotsJoined() - 1)); // floor at 1 (organizer always counted)
        if (match.getStatus() == MatchStatus.CREATED) {
            // Revert to PENDING since we no longer have a full team
            match.setStatus(MatchStatus.PENDING);
        }
        matchRepository.save(match);

        // If the post was FULL, reopen it
        MatchPost post = matchPostRepository.findById(request.getPostId()).orElse(null);
        if (post != null && post.getStatus() == PostStatus.FULL) {
            post.setStatus(PostStatus.OPEN);
            matchPostRepository.save(post);
        }

        // Mark request as CANCELLED (leave ≠ cancel, but both result in the user being out)
        request.setStatus(JoinRequestStatus.CANCELLED);
        request.setRespondedAt(LocalDateTime.now());
        matchJoinRequestRepository.save(request);
    }

    // -------------------------------------------------------------------------
    // LIST VIEWS
    // -------------------------------------------------------------------------

    /** All join requests made by the current user across all posts. */
    public List<JoinRequestDtoResponse> getMyJoinRequests() {
        UUID userId = currentUserService.getCurrentUser().getId();
        List<MatchJoinRequest> requests = matchJoinRequestRepository.findByUserId(userId);
        return requests.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * All join requests submitted to posts created by the current user.
     * Used by the organizer to see who wants to join their posts.
     */
    public List<JoinRequestDtoResponse> getRequestsForMyPosts() {
        UUID organizerId = currentUserService.getCurrentUser().getId();
        List<MatchJoinRequest> requests = matchJoinRequestRepository.findRequestsForPostsOwnedBy(organizerId);
        return requests.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JoinRequestDtoResponse toResponse(MatchJoinRequest request) {
        Profile userProfile = profileRepository.findById(request.getUserId()).orElse(null);
        MatchPost post = matchPostRepository.findById(request.getPostId()).orElse(null);

        return JoinRequestDtoResponse.builder()
                .requestId(request.getId())
                .postId(request.getPostId())
                .matchId(request.getMatchId())
                .userId(request.getUserId())
                .userName(userProfile != null
                        ? userProfile.getFirstName() + " " + userProfile.getLastName() : "Unknown")
                .userAvatarUrl(userProfile != null ? userProfile.getProfilePictureUrl() : null)
                .eloAtRequest(request.getEloAtRequest())
                .status(request.getStatus())
                .createdAt(request.getCreatedAt())
                .respondedAt(request.getRespondedAt())
                .postLocation(post != null ? post.getLocation() : null)
                .postScheduledAt(post != null ? post.getScheduledAt() : null)
                .postMatchType(post != null ? post.getMatchType() : null)
                .postStatus(post != null ? post.getStatus() : null)
                .build();
    }
}
