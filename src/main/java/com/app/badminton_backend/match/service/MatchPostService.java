package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.exceptions.PostNotFoundException;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.CreatePostDtoRequest;
import com.app.badminton_backend.match.dtos.PostDetailDtoResponse;
import com.app.badminton_backend.match.dtos.PostFeedItemDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.*;
import com.app.badminton_backend.match.repository.MatchJoinRequestRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchPostRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchPostService {

    private final CurrentUserService currentUserService;
    private final MatchPostRepository matchPostRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchJoinRequestRepository matchJoinRequestRepository;
    private final EloService eloService;
    private final ProfileRepository profileRepository;

    /**
     * Creates a public open-match post and its companion Match row atomically.
     *
     * Mirror of ChallengeService.createChallengeRoom() but for the OPEN origin:
     *  - slotsTotal derived from matchType (2 for SINGLES, 4 for DOUBLES).
     *  - Match.origin = OPEN; Match.postId = the new post's id.
     *  - Organizer is auto-counted as slotsJoined=1 but NOT added as MatchPlayer
     *    yet (teams are assigned later via the existing assignTeams endpoint).
     *  - expiresAt defaults to scheduledAt + 2 hours.
     */
    @Transactional
    public MatchPost createPost(CreatePostDtoRequest request) {
        UUID creatorId = currentUserService.getCurrentUser().getId();

        LocalDateTime scheduledAt = LocalDateTime.parse(
                request.getScheduledAt(), DateTimeFormatter.ISO_DATE_TIME);

        if (!scheduledAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Scheduled time must be in the future");
        }

        if (request.getEloMin() > request.getEloMax()) {
            throw new IllegalArgumentException("ELO minimum must not exceed ELO maximum");
        }

        int slotsTotal = request.getMatchType() == MatchType.SINGLES ? 2 : 4;

        // Create the companion Match first so we have its ID for the post.
        Match match = matchRepository.save(Match.builder()
                .matchType(request.getMatchType())
                .origin(MatchOrigin.OPEN)
                .status(MatchStatus.PENDING)
                .organizerId(creatorId)
                .slotsTotal(slotsTotal)
                .slotsJoined(1) // organizer auto-counts
                .matchName(request.getTitle()) // Set matchName to the provided title
                .scheduledAt(scheduledAt)
                .build());

        MatchPost post = matchPostRepository.save(MatchPost.builder()
                .creatorId(creatorId)
                .title(request.getTitle())
                .matchType(request.getMatchType())
                .location(request.getLocation())
                .description(request.getDescription())
                .scheduledAt(scheduledAt)
                .eloMin(request.getEloMin())
                .eloMax(request.getEloMax())
                .slotsTotal(slotsTotal)
                .status(PostStatus.OPEN)
                .matchId(match.getId())
                .expiresAt(scheduledAt.plusHours(2))
                .build());

        // Back-fill postId on the Match row now that the post has an ID.
        match.setPostId(post.getId());
        matchRepository.save(match);

        return post;
    }

    /**
     * Paginated feed of open posts the current user can join.
     *
     * Hard exclusions (all enforced in JPQL):
     *  1. User's own posts.
     *  2. Non-OPEN posts (FULL/CANCELLED/EXPIRED are all excluded).
     *  3. Posts where scheduledAt has passed.
     *  4. matchType/elo/date/location filters (optional params — null = no filter).
     */
    public Page<PostFeedItemDtoResponse> getFeed(
            String matchType,
            Integer eloMin,
            Integer eloMax,
            String dateFrom,
            String dateTo,
            String location,
            int page,
            int size) {

        UUID requesterId = currentUserService.getCurrentUser().getId();
        int userElo = eloService.getOrCreate(requesterId).getElo();

        // Build optional params
        String matchTypeParam = (matchType != null && !matchType.isBlank()) ? matchType : null;
        LocalDateTime dateFromParam = (dateFrom != null && !dateFrom.isBlank())
                ? LocalDateTime.parse(dateFrom, DateTimeFormatter.ISO_DATE_TIME) : null;
        LocalDateTime dateToParam = (dateTo != null && !dateTo.isBlank())
                ? LocalDateTime.parse(dateTo, DateTimeFormatter.ISO_DATE_TIME) : null;
        String locationParam = (location != null && !location.isBlank()) ? location : null;

        // Use caller's ELO as the elo filter (hard gate: post range must include user's elo)
        Integer eloFilter = userElo;

        Page<MatchPost> posts = matchPostRepository.findFeed(
                requesterId,
                LocalDateTime.now(),
                matchTypeParam,
                eloFilter,
                dateFromParam,
                dateToParam,
                locationParam,
                PageRequest.of(page, size));

        return posts.map(post -> {
            Match match = matchRepository.findById(post.getMatchId()).orElse(null);
            int slotsJoined = match != null ? match.getSlotsJoined() : 1;

            Profile profile = profileRepository.findById(post.getCreatorId()).orElse(null);
            int organizerElo = eloService.getOrCreate(post.getCreatorId()).getElo();

            return PostFeedItemDtoResponse.builder()
                    .postId(post.getId())
                    .matchId(post.getMatchId())
                    .title(post.getTitle())
                    .matchType(post.getMatchType())
                    .location(post.getLocation())
                    .description(post.getDescription())
                    .scheduledAt(post.getScheduledAt())
                    .eloMin(post.getEloMin())
                    .eloMax(post.getEloMax())
                    .slotsTotal(post.getSlotsTotal())
                    .slotsJoined(slotsJoined)
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .organizerId(post.getCreatorId())
                    .organizerName(profile != null
                            ? profile.getFirstName() + " " + profile.getLastName() : "Unknown")
                    .organizerAvatarUrl(profile != null ? profile.getProfilePictureUrl() : null)
                    .organizerElo(organizerElo)
                    .build();
        });
    }

    /**
     * Full post detail including organizer profile and confirmed roster.
     */
    public PostDetailDtoResponse getPostDetail(UUID postId) {
        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        Match match = matchRepository.findById(post.getMatchId()).orElse(null);
        int slotsJoined = match != null ? match.getSlotsJoined() : 1;

        Profile organizerProfile = profileRepository.findById(post.getCreatorId()).orElse(null);
        int organizerElo = eloService.getOrCreate(post.getCreatorId()).getElo();

        // Build confirmed roster from MatchPlayer rows on the companion match
        List<PostDetailDtoResponse.RosterPlayerDto> roster = new ArrayList<>();
        if (match != null) {
            List<MatchPlayer> players = matchPlayerRepository.findByMatchId(match.getId());
            for (MatchPlayer mp : players) {
                Profile playerProfile = profileRepository.findById(mp.getUserId()).orElse(null);
                int playerElo = eloService.getOrCreate(mp.getUserId()).getElo();
                roster.add(PostDetailDtoResponse.RosterPlayerDto.builder()
                        .userId(mp.getUserId())
                        .name(playerProfile != null
                                ? playerProfile.getFirstName() + " " + playerProfile.getLastName()
                                : "Unknown")
                        .profilePictureUrl(playerProfile != null
                                ? playerProfile.getProfilePictureUrl() : null)
                        .eloRating(playerElo)
                        .build());
            }
        }

        return PostDetailDtoResponse.builder()
                .postId(post.getId())
                .matchId(post.getMatchId())
                .title(post.getTitle())
                .matchType(post.getMatchType())
                .location(post.getLocation())
                .description(post.getDescription())
                .scheduledAt(post.getScheduledAt())
                .eloMin(post.getEloMin())
                .eloMax(post.getEloMax())
                .slotsTotal(post.getSlotsTotal())
                .slotsJoined(slotsJoined)
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .expiresAt(post.getExpiresAt())
                .organizerId(post.getCreatorId())
                .organizerName(organizerProfile != null
                        ? organizerProfile.getFirstName() + " " + organizerProfile.getLastName()
                        : "Unknown")
                .organizerAvatarUrl(organizerProfile != null
                        ? organizerProfile.getProfilePictureUrl() : null)
                .organizerElo(organizerElo)
                .confirmedRoster(roster)
                .build();
    }

    /**
     * Organizer cancels their post.
     *
     * Rules:
     *  - Only the post creator may cancel.
     *  - Only cancellable while status is OPEN or FULL (not already CANCELLED/EXPIRED).
     *  - All still-PENDING join requests are auto-rejected.
     *  - Match is not deleted — it remains in PENDING/CREATED state so accepted
     *    joiners still have their MatchPlayer rows. In a future push-notification
     *    pass, those players would be notified here.
     */
    @Transactional
    public void cancelPost(UUID postId) {
        UUID currentUserId = currentUserService.getCurrentUser().getId();

        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getCreatorId().equals(currentUserId)) {
            throw new UnauthorizedActionException("Only the post creator can cancel this post");
        }

        if (post.getStatus() == PostStatus.CANCELLED || post.getStatus() == PostStatus.EXPIRED) {
            throw new IllegalStateException("Post is already " + post.getStatus().name().toLowerCase());
        }

        post.setStatus(PostStatus.CANCELLED);
        matchPostRepository.save(post);

        // Auto-reject all pending requests for this post
        matchJoinRequestRepository.rejectAllPendingForPost(postId);

        // In-app notification stub: log that accepted joiners need notifying.
        // Real push notifications are deferred to a future pass.
    }

    /**
     * Scheduled job: expire posts whose expiresAt has passed.
     * Runs every 15 minutes. Skips FULL/CANCELLED posts (they're already terminal).
     */
    @Scheduled(fixedRate = 900_000) // every 15 minutes
    @Transactional
    public void expireOldPosts() {
        List<MatchPost> expired = matchPostRepository.findExpiredOpenPosts(LocalDateTime.now());
        for (MatchPost post : expired) {
            post.setStatus(PostStatus.EXPIRED);
            matchPostRepository.save(post);
            // Auto-reject any lingering pending requests on expired posts
            matchJoinRequestRepository.rejectAllPendingForPost(post.getId());
        }
    }
}
