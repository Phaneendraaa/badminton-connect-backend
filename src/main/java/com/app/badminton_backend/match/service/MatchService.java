package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.match.dtos.UnifiedRoomDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.InviteStatus;
import com.app.badminton_backend.match.enums.MatchOrigin;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final CurrentUserService currentUserService;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchInviteRepository matchInviteRepository;
    private final EloPointsRepository eloPointsRepository;

    public Match createMatch(MatchType matchType, MatchOrigin origin, UUID postId,
                             UUID organizerId, List<UUID> teamAUserIds, List<UUID> teamBUserIds) {

        // Validation: singles = 1 per team, doubles = 2 per team
        int expectedPerTeam = matchType == MatchType.SINGLES ? 1 : 2;
        if (teamAUserIds.size() != expectedPerTeam || teamBUserIds.size() != expectedPerTeam) {
            throw new IllegalArgumentException("Invalid number of players for " + matchType);
        }

        Match match = Match.builder()
                .matchType(matchType)
                .origin(origin)
                .postId(postId)
                .organizerId(organizerId)
                .status(MatchStatus.PENDING)
                .build();
        match = matchRepository.save(match);

        // This is where "who is playing" gets recorded
        addPlayers(match.getId(), teamAUserIds, Team.TEAM_A);
        addPlayers(match.getId(), teamBUserIds, Team.TEAM_B);

        return match;
    }

    private void addPlayers(UUID matchId, List<UUID> userIds, Team team) {
        for (UUID userId : userIds) {
            EloPoints elo = eloPointsRepository.findById(userId)
                    .orElseThrow(() -> new IllegalStateException("EloPoints not found for user " + userId));

            MatchPlayer player = MatchPlayer.builder()
                    .matchId(matchId)
                    .userId(userId)
                    .team(team)
                    .eloBefore(elo.getElo())
                    .build();

            matchPlayerRepository.save(player);
        }
    }

    /**
     * Aggregates all matches the current user is confirmed in, regardless of origin.
     *
     * Union strategy:
     *  1. All matches where I am the organizer (any origin: CHALLENGE or OPEN).
     *  2. CHALLENGE-origin matches where I have a MatchInvite with status=JOINED
     *     (and I'm not the organizer — already covered in #1).
     *  3. OPEN-origin matches where I have a MatchPlayer row and I'm NOT the organizer
     *     (these are matches I joined via an accepted join request, not my own posts).
     *
     * De-duplicates by matchId using a LinkedHashMap to preserve insertion order
     * (most-recently-created first after sorting).
     *
     * Excludes COMPLETED matches — those belong in Match History, not active rooms.
     */
    public List<UnifiedRoomDtoResponse> getUnifiedMyRooms() {
        UUID currentUserId = currentUserService.getCurrentUser().getId();

        // Use a map keyed by matchId to automatically de-duplicate.
        Map<UUID, Match> roomMap = new LinkedHashMap<>();

        // Source 1: matches where I'm the organizer
        List<Match> myOrganizerMatches = matchRepository.findByOrganizerId(currentUserId);
        for (Match m : myOrganizerMatches) {
            if (m.getStatus() != MatchStatus.COMPLETED) {
                roomMap.put(m.getId(), m);
            }
        }

        // Source 2: CHALLENGE matches where I have a JOINED invite (non-organizer)
        List<MatchInvite> joinedInvites = matchInviteRepository.findByUserIdAndStatus(
                currentUserId, InviteStatus.JOINED);
        for (MatchInvite invite : joinedInvites) {
            matchRepository.findById(invite.getMatchId()).ifPresent(m -> {
                if (!m.getOrganizerId().equals(currentUserId)
                        && m.getStatus() != MatchStatus.COMPLETED) {
                    roomMap.put(m.getId(), m);
                }
            });
        }

        // Source 3: OPEN matches where I'm a MatchPlayer (joined via open post) but not organizer
        List<MatchPlayer> myPlayerRows = matchPlayerRepository.findByUserId(currentUserId);
        for (MatchPlayer mp : myPlayerRows) {
            matchRepository.findById(mp.getMatchId()).ifPresent(m -> {
                if (!m.getOrganizerId().equals(currentUserId)
                        && m.getStatus() != MatchStatus.COMPLETED) {
                    roomMap.put(m.getId(), m);
                }
            });
        }

        // Sort by scheduledAt descending (most imminent first), then createdAt descending
        List<Match> sorted = new ArrayList<>(roomMap.values());
        sorted.sort((a, b) -> {
            var timeA = a.getScheduledAt() != null ? a.getScheduledAt() : a.getCreatedAt();
            var timeB = b.getScheduledAt() != null ? b.getScheduledAt() : b.getCreatedAt();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeA.compareTo(timeB); // ascending: soonest first
        });

        return sorted.stream()
                .map(m -> UnifiedRoomDtoResponse.builder()
                        .matchId(m.getId())
                        .matchName(m.getMatchName())
                        .matchType(m.getMatchType())
                        .origin(m.getOrigin())
                        .status(m.getStatus())
                        .scheduledAt(m.getScheduledAt())
                        .slotsJoined(m.getSlotsJoined())
                        .slotsTotal(m.getSlotsTotal())
                        .createdAt(m.getCreatedAt())
                        .postId(m.getPostId())
                        .build())
                .collect(Collectors.toList());
    }
}
