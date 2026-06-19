package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.enums.InviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchInviteRepository extends JpaRepository<MatchInvite, UUID> {
    List<MatchInvite> findByMatchId(UUID matchId);
    List<MatchInvite> findByUserIdAndStatus(UUID userId, InviteStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM MatchInvite m WHERE m.matchId = :matchId")
    void deleteByMatchId(@org.springframework.data.repository.query.Param("matchId") UUID matchId);
}