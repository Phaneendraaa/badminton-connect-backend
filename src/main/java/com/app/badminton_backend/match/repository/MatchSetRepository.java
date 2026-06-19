package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MatchSetRepository extends JpaRepository<MatchSet, UUID> {
    Optional<MatchSet> findByMatchIdAndSetNumber(UUID matchId, Integer setNumber);
    List<MatchSet> findByMatchId(UUID matchId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("DELETE FROM MatchSet m WHERE m.matchId = :matchId")
    void deleteByMatchId(@org.springframework.data.repository.query.Param("matchId") UUID matchId);
}
