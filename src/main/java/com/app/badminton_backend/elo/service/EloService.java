package com.app.badminton_backend.elo.service;
import com.app.badminton_backend.elo.EloCalculator;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EloService {

    private final EloPointsRepository eloPointsRepository;
    private final EloCalculator eloCalculator;

    public EloPoints getOrCreate(UUID userId) {
        return eloPointsRepository.findById(userId)
                .orElseGet(() -> eloPointsRepository.save(
                        EloPoints.builder().id(userId).build() // defaults to 1000
                ));
    }

    /**
     * Singles: pass single user IDs.
     * Doubles: pass both teammates' IDs — their average rating is used as the "team rating".
     */
    public void applyMatchResult(List<UUID> winningTeamUserIds, List<UUID> losingTeamUserIds) {

        List<EloPoints> winners = winningTeamUserIds.stream().map(this::getOrCreate).toList();
        List<EloPoints> losers = losingTeamUserIds.stream().map(this::getOrCreate).toList();

        int winnerTeamRating = averageRating(winners);
        int loserTeamRating = averageRating(losers);

        int winnerChange = eloCalculator.calculateEloChange(winnerTeamRating, loserTeamRating, 1.0);
        int loserChange = eloCalculator.calculateEloChange(loserTeamRating, winnerTeamRating, 0.0);

        applyChange(winners, winnerChange);
        applyChange(losers, loserChange);
    }

    private int averageRating(List<EloPoints> players) {
        return (int) Math.round(players.stream().mapToInt(EloPoints::getElo).average().orElse(1000));
    }
    private void applyChange(List<EloPoints> players, int change) {
        for (EloPoints ep : players) {
            ep.setElo(ep.getElo() + change);
            eloPointsRepository.save(ep);
        }
    }
}