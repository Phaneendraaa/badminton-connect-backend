package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {
    List<MatchPlayer> findByMatchId(UUID matchId);
    List<MatchPlayer> findByMatchIdAndTeam(UUID matchId, Team team);

    /** Find all MatchPlayer rows for a given user — used by getUnifiedMyRooms(). */
    List<MatchPlayer> findByUserId(UUID userId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM MatchPlayer m WHERE m.matchId = :matchId")
    void deleteByMatchId(@org.springframework.data.repository.query.Param("matchId") UUID matchId);

    /**
     * Count completed matches for a user.
     * Joins MatchPlayer → Match so status is checked in a single query — no N+1.
     */
    @Query("SELECT COUNT(mp) FROM MatchPlayer mp " +
           "JOIN Match m ON m.id = mp.matchId " +
           "WHERE mp.userId = :userId AND m.status = com.app.badminton_backend.match.enums.MatchStatus.COMPLETED")
    long countCompletedMatchesByUserId(@Param("userId") UUID userId);

    /**
     * Count wins for a user: matches where the user's team equals the match's winning team.
     * winnerTeam is null until a match is confirmed, so only COMPLETED rows have it set.
     */
    @Query("SELECT COUNT(mp) FROM MatchPlayer mp " +
           "JOIN Match m ON m.id = mp.matchId " +
           "WHERE mp.userId = :userId " +
           "AND m.status = com.app.badminton_backend.match.enums.MatchStatus.COMPLETED " +
           "AND mp.team = m.winnerTeam")
    long countWinsByUserId(@Param("userId") UUID userId);

    @Query("SELECT mp FROM MatchPlayer mp " +
           "JOIN Match m ON m.id = mp.matchId " +
           "WHERE mp.userId = :userId " +
           "AND m.status = com.app.badminton_backend.match.enums.MatchStatus.COMPLETED " +
           "ORDER BY m.playedAt DESC")
    List<MatchPlayer> findCompletedMatchesByUserId(@Param("userId") UUID userId);
}