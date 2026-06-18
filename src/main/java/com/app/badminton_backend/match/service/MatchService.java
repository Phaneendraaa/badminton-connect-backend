package com.app.badminton_backend.match.service;

import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.MatchOrigin;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
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
}
