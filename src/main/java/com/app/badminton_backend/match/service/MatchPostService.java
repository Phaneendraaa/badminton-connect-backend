package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.exceptions.PostNotFoundException;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.CreatePostDtoRequest;
import com.app.badminton_backend.match.dtos.MyPostDtoResponse;
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
import com.app.badminton_backend.match.repository.NotificationRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import com.app.badminton_backend.reference.ReferenceController;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /**
     * Creates a public open-match post and its companion Match row atomically.
     *
     * City validation rules (enforced here because they are cross-field):
     *  1. city must be a known value from ReferenceController.CITIES or "Other".
     *  2. If city == "Other", cityOther must be non-blank.
     *  3. If city != "Other", cityOther must be null.
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

        // City cross-field validation
        validateCity(request.getCity(), request.getCityOther());

        int slotsTotal = request.getMatchType() == MatchType.SINGLES ? 2 : 4;

        // Create the companion Match first so we have its ID for the post.
        Match match = matchRepository.save(Match.builder()
                .matchType(request.getMatchType())
                .origin(MatchOrigin.OPEN)
                .status(MatchStatus.PENDING)
                .organizerId(creatorId)
                .slotsTotal(slotsTotal)
                .slotsJoined(1) // organizer auto-counts
                .matchName(request.getTitle())
                .scheduledAt(scheduledAt)
                .build());

        MatchPost post = matchPostRepository.save(MatchPost.builder()
                .creatorId(creatorId)
                .title(request.getTitle())
                .matchType(request.getMatchType())
                .city(request.getCity())
                .cityOther("Other".equals(request.getCity()) ? request.getCityOther() : null)
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

        // Save the organizer as the first player in the roster (UNASSIGNED until formation step).
        matchPlayerRepository.save(MatchPlayer.builder()
                .matchId(match.getId())
                .userId(creatorId)
                .team(Team.UNASSIGNED)
                .eloBefore(eloService.getOrCreate(creatorId).getElo())
                .build());

        // Back-fill postId on the Match row now that the post has an ID.
        match.setPostId(post.getId());
        matchRepository.save(match);

        return post;
    }

    /**
     * Validates the city/cityOther invariant.
     *
     * Enforces both directions:
     *  - city must be in the curated list or "Other".
     *  - If city == "Other", cityOther must be non-blank.
     *  - If city != "Other", cityOther must be null/blank (reject junk data).
     */
    private void validateCity(String city, String cityOther) {
        if (!ReferenceController.isValidCity(city)) {
            throw new IllegalArgumentException(
                    "Invalid city value: '" + city + "'. Use one of the values from GET /reference/cities.");
        }
        if ("Other".equals(city)) {
            if (cityOther == null || cityOther.isBlank()) {
                throw new IllegalArgumentException(
                        "cityOther is required when city is 'Other'");
            }
        } else {
            if (cityOther != null && !cityOther.isBlank()) {
                throw new IllegalArgumentException(
                        "cityOther must be empty when city is not 'Other'");
            }
        }
    }

    /**
     * Paginated feed of open posts the current user can join.
     *
     * Hard exclusions (all enforced in JPQL):
     *  1. User's own posts.
     *  2. Non-OPEN posts (FULL/CANCELLED/EXPIRED are all excluded).
     *  3. Posts where scheduledAt has passed.
     *  4. matchType/elo/date/location/city filters (optional params — null = no filter).
     */
    public Page<PostFeedItemDtoResponse> getFeed(
            String matchType,
            Integer eloMin,
            Integer eloMax,
            String dateFrom,
            String dateTo,
            String location,
            String city,
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
        String cityParam = (city != null && !city.isBlank()) ? city : null;

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
                cityParam,
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
                    .city(post.getCity())
                    .cityOther(post.getCityOther())
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
     * Returns all posts created by the current user (organizer view),
     * enriched with pending request count for the My Posts tab badge.
     *
     * Includes all statuses (OPEN, FULL, CANCELLED, EXPIRED) so organizers
     * can see their full history, not just active posts.
     */
    public List<MyPostDtoResponse> getMyPosts() {
        UUID creatorId = currentUserService.getCurrentUser().getId();
        List<MatchPost> posts = matchPostRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId);

        if (posts.isEmpty()) {
            return List.of();
        }

        // Fetch pending request counts in a single aggregate query (avoids N+1).
        List<UUID> postIds = posts.stream().map(MatchPost::getId).collect(Collectors.toList());
        List<Object[]> rawCounts = matchPostRepository.countPendingRequestsForPosts(postIds);

        Map<UUID, Integer> pendingCounts = new HashMap<>();
        for (Object[] row : rawCounts) {
            UUID postId = (UUID) row[0];
            Long count = (Long) row[1];
            pendingCounts.put(postId, count.intValue());
        }

        List<MyPostDtoResponse> result = new ArrayList<>();
        for (MatchPost post : posts) {
            Match match = matchRepository.findById(post.getMatchId()).orElse(null);
            int slotsJoined = match != null ? match.getSlotsJoined() : 1;

            result.add(MyPostDtoResponse.builder()
                    .postId(post.getId())
                    .matchId(post.getMatchId())
                    .title(post.getTitle())
                    .matchType(post.getMatchType())
                    .city(post.getCity())
                    .cityOther(post.getCityOther())
                    .location(post.getLocation())
                    .scheduledAt(post.getScheduledAt())
                    .eloMin(post.getEloMin())
                    .eloMax(post.getEloMax())
                    .slotsTotal(post.getSlotsTotal())
                    .slotsJoined(slotsJoined)
                    .status(post.getStatus())
                    .createdAt(post.getCreatedAt())
                    .pendingRequestCount(pendingCounts.getOrDefault(post.getId(), 0))
                    .build());
        }
        return result;
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
                .city(post.getCity())
                .cityOther(post.getCityOther())
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

        // Notify all confirmed MatchPlayers that the match is cancelled
        Match match = matchRepository.findById(post.getMatchId()).orElse(null);
        if (match != null) {
            List<MatchPlayer> players = matchPlayerRepository.findByMatchId(match.getId());
            for (MatchPlayer mp : players) {
                notificationService.create(
                        mp.getUserId(),
                        NotificationType.POST_CANCELLED,
                        postId,
                        match.getId(),
                        "The match \"" + match.getMatchName() + "\" has been cancelled by the organizer.");
            }
        }
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

    /**
     * Scheduled job: fire MATCH_STARTING_SOON notifications for matches whose
     * scheduledAt falls within the next 55–65 minutes.
     *
     * Runs every 5 minutes. Guards against double-firing by checking whether a
     * MATCH_STARTING_SOON notification for that matchId + userId already exists
     * before creating a new one.
     */
    @Scheduled(fixedRate = 300_000) // every 5 minutes
    @Transactional
    public void fireStartingSoonNotifications() {
        LocalDateTime windowStart = LocalDateTime.now().plusMinutes(55);
        LocalDateTime windowEnd   = LocalDateTime.now().plusMinutes(65);

        // Find matches whose scheduledAt is in the [+55min, +65min] window — DB-level filter, no full scan.
        List<Match> upcoming = matchRepository.findUpcomingInWindow(windowStart, windowEnd);

        for (Match match : upcoming) {
            List<MatchPlayer> players = matchPlayerRepository.findByMatchId(match.getId());
            for (MatchPlayer mp : players) {
                // Duplicate guard: skip if we already fired this notification
                boolean alreadySent = notificationRepository
                        .findByUserIdAndRelatedMatchIdAndType(
                                mp.getUserId(), match.getId(), NotificationType.MATCH_STARTING_SOON)
                        .isPresent();
                if (!alreadySent) {
                    notificationService.create(
                            mp.getUserId(),
                            NotificationType.MATCH_STARTING_SOON,
                            match.getPostId(),
                            match.getId(),
                            "Your match \"" + match.getMatchName() + "\" starts in about 1 hour!");
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // EXTEND POST (organizer reschedules a FULL/confirmed match)
    // -------------------------------------------------------------------------

    /**
     * Organizer extends the scheduled time of a FULL post after confirmation.
     *
     * Validation:
     *  - Only the post creator may call this.
     *  - Post must be FULL (confirmed, not OPEN/EXPIRED/CANCELLED).
     *  - newScheduledAt must be strictly in the future.
     *  - newExpiresAt must be after newScheduledAt.
     *  - newExpiresAt must be within 24 hours of the current expiresAt (extension cap per call).
     *
     * Side effects:
     *  - MatchPost.scheduledAt and MatchPost.expiresAt updated.
     *  - Match.scheduledAt updated (so MATCH_STARTING_SOON job stays accurate).
     *  - MATCH_TIME_EXTENDED in-app notification sent to all confirmed MatchPlayers.
     */
    @Transactional
    public void extendPost(UUID postId, LocalDateTime newScheduledAt, LocalDateTime newExpiresAt) {
        UUID callerId = currentUserService.getCurrentUser().getId();

        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        if (!post.getCreatorId().equals(callerId)) {
            throw new UnauthorizedActionException("Only the post creator can extend the play time");
        }

        if (post.getStatus() != PostStatus.FULL) {
            throw new IllegalStateException(
                    "Play time can only be extended for a FULL (confirmed) post. Current status: " + post.getStatus());
        }

        if (!newScheduledAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("New scheduled time must be in the future");
        }

        if (!newExpiresAt.isAfter(newScheduledAt)) {
            throw new IllegalArgumentException("New expiry time must be after the new scheduled time");
        }

        // 24-hour extension cap per call
        LocalDateTime currentExpiresAt = post.getExpiresAt() != null
                ? post.getExpiresAt()
                : post.getScheduledAt().plusHours(2);
        if (newExpiresAt.isAfter(currentExpiresAt.plusHours(24))) {
            throw new IllegalArgumentException(
                    "Extension is capped at 24 hours beyond the current expiry time (" + currentExpiresAt + "). " +
                    "Call extend again to extend further if needed.");
        }

        // Update the post
        post.setScheduledAt(newScheduledAt);
        post.setExpiresAt(newExpiresAt);
        matchPostRepository.save(post);

        // Update the companion Match so scheduledAt-based jobs stay correct
        Match match = matchRepository.findById(post.getMatchId()).orElse(null);
        if (match != null) {
            match.setScheduledAt(newScheduledAt);
            matchRepository.save(match);

            // Notify all confirmed players
            List<MatchPlayer> players = matchPlayerRepository.findByMatchId(match.getId());
            String formattedTime = newScheduledAt.format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
            for (MatchPlayer mp : players) {
                notificationService.create(
                        mp.getUserId(),
                        NotificationType.MATCH_TIME_EXTENDED,
                        postId,
                        match.getId(),
                        "The match \"" + match.getMatchName() + "\" has been rescheduled to " + formattedTime + ".");
            }
        }
    }
}
