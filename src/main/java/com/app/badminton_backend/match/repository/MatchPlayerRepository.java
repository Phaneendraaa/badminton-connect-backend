package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.Team;
import org.springframework.data.jpa.repository.JpaRepository;

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
}