package com.app.badminton_backend.match.service;

import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.match.dtos.MatchPlayerDto;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.entity.MatchSet;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.match.repository.MatchSetRepository;
import com.app.badminton_backend.match.dtos.MatchSetDtoRequest;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchPlayService {
    private final MatchRepository matchRepository;
    private final MatchSetRepository matchSetRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final EloService eloService;
    private final ProfileRepository profileRepository;

    public void startMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow();

        if (match.getStatus() != MatchStatus.CREATED) {
            throw new IllegalStateException("Match can only be started after teams are assigned (status must be CREATED)");
        }

        match.setStatus(MatchStatus.PLAYING);
        match.setPlayedAt(LocalDateTime.now());
        matchRepository.save(match);
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
    }

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
    }

    public Match getMatch(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow();
    }

    public List<MatchSet> getMatchSets(UUID matchId) {
        return matchSetRepository.findByMatchId(matchId);
    }

    public List<MatchPlayerDto> getMatchPlayers(UUID matchId) {
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

    public void deleteMatchSet(UUID matchId, Integer setNumber) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new RuntimeException("Match not found"));
        if (match.getStatus() != MatchStatus.PLAYING) {
            throw new IllegalStateException("Cannot delete sets unless match is PLAYING");
        }
        
        MatchSet set = matchSetRepository.findByMatchIdAndSetNumber(matchId, setNumber)
                .orElseThrow(() -> new RuntimeException("Set not found"));
        
        matchSetRepository.delete(set);
    }
}

