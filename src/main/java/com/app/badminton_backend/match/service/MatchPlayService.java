package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.exceptions.PostNotFoundException;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.AssignTeamsDtoRequest;
import com.app.badminton_backend.match.dtos.MatchDetailDtoResponse;
import com.app.badminton_backend.match.dtos.MatchPlayerDto;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.entity.MatchPlayerRemovalLog;
import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.entity.MatchSet;
import com.app.badminton_backend.match.enums.JoinRequestStatus;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.NotificationType;
import com.app.badminton_backend.match.enums.PostStatus;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.repository.MatchJoinRequestRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRemovalLogRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchPostRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.match.repository.MatchSetRepository;
import com.app.badminton_backend.match.dtos.MatchSetDtoRequest;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchPlayService {
    private final CurrentUserService currentUserService;
    private final MatchRepository matchRepository;
    private final MatchPostRepository matchPostRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchPlayerRemovalLogRepository removalLogRepository;
    private final MatchJoinRequestRepository matchJoinRequestRepository;
    private final EloService eloService;
    private final ProfileRepository profileRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    // ── STOMP topic prefix for live match-play state ──────────────────────────
    private static final String MATCH_PLAY_TOPIC = "/topic/match-play/";

    // -------------------------------------------------------------------------
    // START MATCH
    // -------------------------------------------------------------------------

    /**
     * Organizer starts the match.
     *
     * Guards:
     *  - Match must be CREATED (all slots filled) — not PENDING, PLAYING, or COMPLETED.
     *  - Every MatchPlayer row must have team != UNASSIGNED, otherwise team formation
     *    has not been completed and we reject the start.
     */
    @Transactional
    public void startMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new IllegalArgumentException("Match not found: " + matchId));

        if (match.getStatus() != MatchStatus.CREATED) {
            throw new IllegalStateException(
                    "Match can only be started after all slots are filled (status must be CREATED, current: "
                    + match.getStatus() + ")");
        }

        // Guard: all players must have been assigned to a team
        List<MatchPlayer> unassigned = matchPlayerRepository.findByMatchId(matchId).stream()
                .filter(mp -> mp.getTeam() == Team.UNASSIGNED)
                .collect(Collectors.toList());
        if (!unassigned.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot start match: " + unassigned.size() +
                    " player(s) are still UNASSIGNED. Complete team formation first via /assign-teams.");
        }

        match.setStatus(MatchStatus.PLAYING);
        match.setPlayedAt(LocalDateTime.now());
        matchRepository.save(match);

        broadcastState(matchId);
    }

    // -------------------------------------------------------------------------
    // ASSIGN TEAMS (formation step)
    // -------------------------------------------------------------------------

    /**
     * Organizer assigns all confirmed players to TEAM_A or TEAM_B, and optionally
     * sets custom team display names.
     *
     * Guards:
     *  - Only the organizer may call this.
     *  - Match must be in CREATED status (all slots filled, but not yet PLAYING).
     *  - Every MatchPlayer for this match must appear in exactly one of teamAUserIds / teamBUserIds.
     *  - Team sizes must match the match type (1+1 for SINGLES, 2+2 for DOUBLES).
     */
    @Transactional
    public void assignTeams(UUID matchId, AssignTeamsDtoRequest request) {
        UUID callerId = currentUserService.getCurrentUser().getId();

        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new IllegalArgumentException("Match not found: " + matchId));

        // Organizer guard
        if (!match.getOrganizerId().equals(callerId)) {
            throw new UnauthorizedActionException("Only the match organizer can assign teams");
        }

        // Status guard — only valid before PLAYING
        if (match.getStatus() != MatchStatus.CREATED) {
            throw new IllegalStateException(
                    "Team assignment is only allowed when match is CREATED (current: " + match.getStatus() + ")");
        }

        List<UUID> teamAIds = request.getTeamAUserIds() != null ? request.getTeamAUserIds() : List.of();
        List<UUID> teamBIds = request.getTeamBUserIds() != null ? request.getTeamBUserIds() : List.of();

        // Size validation
        int expectedPerTeam = match.getMatchType() == MatchType.SINGLES ? 1 : 2;
        if (teamAIds.size() != expectedPerTeam || teamBIds.size() != expectedPerTeam) {
            throw new IllegalArgumentException(
                    "Expected " + expectedPerTeam + " player(s) per team for " + match.getMatchType()
                    + " but got Team A=" + teamAIds.size() + ", Team B=" + teamBIds.size());
        }

        // No duplicates across teams
        Set<UUID> allInRequest = new HashSet<>(teamAIds);
        for (UUID id : teamBIds) {
            if (!allInRequest.add(id)) {
                throw new IllegalArgumentException(
                        "Player " + id + " appears in both Team A and Team B");
            }
        }

        // Every match player must appear in the request
        List<MatchPlayer> allPlayers = matchPlayerRepository.findByMatchId(matchId);
        Set<UUID> allPlayerIds = allPlayers.stream()
                .map(MatchPlayer::getUserId).collect(Collectors.toSet());

        if (!allPlayerIds.equals(allInRequest)) {
            throw new IllegalArgumentException(
                    "The provided player lists do not match the match's confirmed roster. " +
                    "Every accepted player must be assigned to exactly one team.");
        }

        // Apply assignments
        for (MatchPlayer mp : allPlayers) {
            if (teamAIds.contains(mp.getUserId())) {
                mp.setTeam(Team.TEAM_A);
            } else {
                mp.setTeam(Team.TEAM_B);
            }
            matchPlayerRepository.save(mp);
        }

        // Apply optional custom names
        if (request.getTeamAName() != null && !request.getTeamAName().isBlank()) {
            match.setTeamAName(request.getTeamAName().trim());
        }
        if (request.getTeamBName() != null && !request.getTeamBName().isBlank()) {
            match.setTeamBName(request.getTeamBName().trim());
        }
        matchRepository.save(match);

        broadcastState(matchId);
    }

    // -------------------------------------------------------------------------
    // REMOVE PLAYER (organizer-driven eviction)
    // -------------------------------------------------------------------------

    /**
     * Organizer removes a confirmed player from the match before it starts.
     *
     * Effects:
     *  1. MatchPlayer row for that user is deleted.
     *  2. An audit log entry is written to match_player_removal_log.
     *  3. Match.slotsJoined is decremented (floor 1 — organizer always counts).
     *  4. If Match.status was CREATED (full), it reverts to PENDING.
     *  5. If MatchPost.status was FULL, it reverts to OPEN (slot is available again).
     *  6. The removed player's MatchJoinRequest is marked REJECTED.
     *  7. An in-app PLAYER_REMOVED notification is sent to the removed player.
     *  8. A STOMP broadcast is sent so all connected clients (including the removed
     *     player's device) learn of the change immediately — the removed player's
     *     client should react by leaving the chat/match-play screens.
     *
     * Guards:
     *  - Only the match organizer may call this.
     *  - Only allowed while match status is CREATED or PENDING (not PLAYING/COMPLETED).
     *  - Cannot remove the organizer themselves.
     */
    @Transactional
    public void removePlayer(UUID matchId, UUID targetUserId, String reason) {
        UUID callerId = currentUserService.getCurrentUser().getId();

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new PostNotFoundException("Match not found: " + matchId));

        // Organizer-only guard
        if (!match.getOrganizerId().equals(callerId)) {
            throw new UnauthorizedActionException("Only the match organizer can remove players");
        }

        // Cannot remove self (organizer)
        if (callerId.equals(targetUserId)) {
            throw new IllegalArgumentException("The organizer cannot remove themselves from the match");
        }

        // Status guard — no removal during or after play
        if (match.getStatus() == MatchStatus.PLAYING || match.getStatus() == MatchStatus.COMPLETED) {
            throw new IllegalStateException(
                    "Cannot remove a player once the match is " + match.getStatus());
        }

        // Find and remove the MatchPlayer row
        List<MatchPlayer> toRemove = matchPlayerRepository.findByMatchId(matchId).stream()
                .filter(mp -> mp.getUserId().equals(targetUserId))
                .collect(Collectors.toList());

        if (toRemove.isEmpty()) {
            throw new IllegalArgumentException(
                    "User " + targetUserId + " is not a confirmed player in this match");
        }
        matchPlayerRepository.deleteAll(toRemove);

        // Write audit log
        removalLogRepository.save(MatchPlayerRemovalLog.builder()
                .matchId(matchId)
                .removedUserId(targetUserId)
                .removedByUserId(callerId)
                .reason(reason)
                .build());

        // Decrement slotsJoined (floor at 1 — organizer always occupies a slot)
        match.setSlotsJoined(Math.max(1, match.getSlotsJoined() - 1));
        if (match.getStatus() == MatchStatus.CREATED) {
            match.setStatus(MatchStatus.PENDING);
        }
        matchRepository.save(match);

        // Re-open the post if it was FULL
        if (match.getPostId() != null) {
            matchPostRepository.findById(match.getPostId()).ifPresent(post -> {
                if (post.getStatus() == PostStatus.FULL) {
                    post.setStatus(PostStatus.OPEN);
                    matchPostRepository.save(post);
                }
            });

            // Mark the removed player's join request as REJECTED so they must re-request
            matchJoinRequestRepository
                    .findByPostIdAndUserId(match.getPostId(), targetUserId)
                    .ifPresent(req -> {
                        req.setStatus(JoinRequestStatus.REJECTED);
                        req.setRespondedAt(LocalDateTime.now());
                        matchJoinRequestRepository.save(req);
                    });
        }

        // Notify the removed player
        notificationService.create(
                targetUserId,
                NotificationType.PLAYER_REMOVED,
                match.getPostId(),
                matchId,
                "You have been removed from the match \"" + match.getMatchName() + "\" by the organizer.");

        // Broadcast STOMP event — type PLAYER_REMOVED so clients can react
        // (the removed player's client should navigate away from match-related screens)
        broadcastSystemEvent(matchId, "PLAYER_REMOVED", targetUserId.toString());

        // Also broadcast updated match-play state
        broadcastState(matchId);
    }

    public void addMatchSet(UUID matchId, MatchSetDtoRequest request) {
        Match match = matchRepository.findById(matchId).orElseThrow();
        if (match.getStatus() != MatchStatus.PLAYING) {
            throw new IllegalStateException("Cannot add sets unless match is PLAYING");
        }

        MatchSet set = matchSetRepository.findByMatchIdAndSetNumber(matchId, request.getSetNumber()).orElse(null);

        Team winner = null;
        if (request.getTeamAScore() > request.getTeamBScore()) {
            winner = Team.TEAM_A;
        } else if (request.getTeamBScore() > request.getTeamAScore()) {
            winner = Team.TEAM_B;
        }

        if (set == null) {
            set = MatchSet.builder()
                    .matchId(matchId)
                    .setNumber(request.getSetNumber())
                    .teamAScore(request.getTeamAScore())
                    .teamBScore(request.getTeamBScore())
                    .setWinner(winner)
                    .build();
        } else {
            set.setTeamAScore(request.getTeamAScore());
            set.setTeamBScore(request.getTeamBScore());
            set.setWinner(winner);
        }
        matchSetRepository.save(set);

        broadcastState(matchId);
    }

    // -------------------------------------------------------------------------
    // FINISH MATCH
    // -------------------------------------------------------------------------

    @Transactional
    public void finishMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow();

        if (match.getStatus() != MatchStatus.PLAYING) {
            throw new IllegalStateException("Match is not currently playing");
        }

        List<MatchSet> sets = matchSetRepository.findByMatchId(matchId);
        if (sets.isEmpty()) {
            throw new IllegalStateException("Cannot finish a match with no sets played");
        }

        match.setStatus(MatchStatus.COMPLETED);

        int teamAWins = 0;
        int teamBWins = 0;
        for (MatchSet s : sets) {
            if (s.getSetWinner() == Team.TEAM_A)
                teamAWins++;
            else if (s.getSetWinner() == Team.TEAM_B)
                teamBWins++;
        }

        if (teamAWins > teamBWins)
            match.setWinnerTeam(Team.TEAM_A);
        else if (teamBWins > teamAWins)
            match.setWinnerTeam(Team.TEAM_B);
        // If equal, winnerTeam stays null (draw) — no ELO changes

        matchRepository.save(match);

        // Apply ELO changes if there is a clear winner
        if (match.getWinnerTeam() != null) {
            Team winnerTeam = match.getWinnerTeam();
            Team loserTeam = (winnerTeam == Team.TEAM_A) ? Team.TEAM_B : Team.TEAM_A;

            List<MatchPlayer> winners = matchPlayerRepository.findByMatchIdAndTeam(matchId, winnerTeam);
            List<MatchPlayer> losers = matchPlayerRepository.findByMatchIdAndTeam(matchId, loserTeam);

            List<UUID> winnerUserIds = winners.stream().map(MatchPlayer::getUserId).toList();
            List<UUID> loserUserIds = losers.stream().map(MatchPlayer::getUserId).toList();

            // Apply ELO via EloService
            eloService.applyMatchResult(winnerUserIds, loserUserIds);

            // Record eloAfter and eloChange on each MatchPlayer
            for (MatchPlayer mp : winners) {
                EloPoints currentElo = eloService.getOrCreate(mp.getUserId());
                mp.setEloAfter(currentElo.getElo());
                mp.setEloChange(currentElo.getElo() - mp.getEloBefore());
                matchPlayerRepository.save(mp);
            }
            for (MatchPlayer mp : losers) {
                EloPoints currentElo = eloService.getOrCreate(mp.getUserId());
                mp.setEloAfter(currentElo.getElo());
                mp.setEloChange(currentElo.getElo() - mp.getEloBefore());
                matchPlayerRepository.save(mp);
            }
        }

        broadcastState(matchId);
    }

    // -------------------------------------------------------------------------
    // DELETE SET
    // -------------------------------------------------------------------------

    public void deleteMatchSet(UUID matchId, Integer setNumber) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new RuntimeException("Match not found"));
        if (match.getStatus() != MatchStatus.PLAYING) {
            throw new IllegalStateException("Cannot delete sets unless match is PLAYING");
        }

        MatchSet set = matchSetRepository.findByMatchIdAndSetNumber(matchId, setNumber)
                .orElseThrow(() -> new RuntimeException("Set not found"));

        matchSetRepository.delete(set);

        broadcastState(matchId);
    }

    // -------------------------------------------------------------------------
    // READ — getMatchDetail (replaces the three individual get* methods)
    // -------------------------------------------------------------------------

    /**
     * Returns a typed MatchDetailDtoResponse combining match metadata, sets, and players.
     * Replaces the previous Map.of(...) pattern in the controller.
     */
    public MatchDetailDtoResponse getMatchDetail(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(
                () -> new IllegalArgumentException("Match not found: " + matchId));
        List<MatchSet> sets = matchSetRepository.findByMatchId(matchId);
        List<MatchPlayerDto> players = buildPlayerDtos(matchId);

        return MatchDetailDtoResponse.builder()
                .match(match)
                .sets(sets)
                .players(players)
                .build();
    }

    // Kept for any remaining internal callers
    public Match getMatch(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow();
    }

    public List<MatchSet> getMatchSets(UUID matchId) {
        return matchSetRepository.findByMatchId(matchId);
    }

    public List<MatchPlayerDto> getMatchPlayers(UUID matchId) {
        return buildPlayerDtos(matchId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<MatchPlayerDto> buildPlayerDtos(UUID matchId) {
        List<MatchPlayer> players = matchPlayerRepository.findByMatchId(matchId);
        List<MatchPlayerDto> dtos = new ArrayList<>();

        for (MatchPlayer mp : players) {
            Profile profile = profileRepository.findById(mp.getUserId()).orElse(null);
            String name = profile != null
                    ? profile.getFirstName() + " " + profile.getLastName()
                    : "Unknown";
            String profilePictureUrl = profile != null ? profile.getProfilePictureUrl() : null;

            dtos.add(MatchPlayerDto.builder()
                    .userId(mp.getUserId())
                    .name(name)
                    .profilePictureUrl(profilePictureUrl)
                    .team(mp.getTeam())
                    .eloBefore(mp.getEloBefore())
                    .eloAfter(mp.getEloAfter())
                    .eloChange(mp.getEloChange())
                    .build());
        }
        return dtos;
    }

    // -------------------------------------------------------------------------
    // Private: broadcast full match-play state to all subscribers
    // -------------------------------------------------------------------------

    /**
     * Publishes the complete MatchDetailDtoResponse to /topic/match-play/{matchId}.
     * Called after every state-changing action so all connected clients replace
     * their local state wholesale — no diffing needed on the frontend side.
     *
     * Uses the same DTO as GET /match-play/{matchId}, meaning the frontend can
     * reuse identical parsing logic for both the initial REST fetch and every
     * subsequent socket push.
     */
    private void broadcastState(UUID matchId) {
        try {
            MatchDetailDtoResponse state = getMatchDetail(matchId);
            messagingTemplate.convertAndSend(MATCH_PLAY_TOPIC + matchId, state);
            log.debug("[MatchPlay] Broadcast state for match {} → status={}",
                    matchId, state.getMatch().getStatus());
        } catch (Exception ex) {
            // Non-fatal: DB write already succeeded; just log so we don't
            // roll back the transaction over a failed broadcast.
            log.warn("[MatchPlay] Failed to broadcast state for match {}: {}",
                    matchId, ex.getMessage());
        }
    }

    /**
     * Broadcasts a lightweight system event to /topic/match/{matchId}.
     * Used for events like PLAYER_REMOVED where the client needs to act
     * immediately (navigate away) before a full state refresh is useful.
     *
     * Payload structure: { "eventType": "...", "payload": "..." }
     */
    private void broadcastSystemEvent(UUID matchId, String eventType, String payload) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/match/" + matchId,
                    java.util.Map.of("eventType", eventType, "payload", payload));
        } catch (Exception ex) {
            log.warn("[MatchPlay] Failed to broadcast system event {} for match {}: {}",
                    eventType, matchId, ex.getMessage());
        }
    }
}
