package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchPlayerRemovalLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchPlayerRemovalLogRepository extends JpaRepository<MatchPlayerRemovalLog, UUID> {

    /** All removal events for a given match — ordered newest first. */
    List<MatchPlayerRemovalLog> findByMatchIdOrderByRemovedAtDesc(UUID matchId);
}
