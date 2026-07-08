package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByOrganizerId(UUID organizerId);

    /** Used by getUnifiedMyRooms() to look up OPEN-origin matches by their postId. */
    List<Match> findByPostIdIn(List<UUID> postIds);

    /**
     * DB-level filter for the starting-soon notification job.
     * Returns only matches whose scheduledAt is strictly within [windowStart, windowEnd]
     * and whose status is not COMPLETED or CANCELLED.
     * Replaces the previous findAll().stream().filter(...) full-table scan.
     */
    @Query("SELECT m FROM Match m " +
           "WHERE m.scheduledAt BETWEEN :windowStart AND :windowEnd " +
           "AND m.status NOT IN (" +
           "  com.app.badminton_backend.match.enums.MatchStatus.COMPLETED, " +
           "  com.app.badminton_backend.match.enums.MatchStatus.CANCELLED)")
    List<Match> findUpcomingInWindow(@Param("windowStart") LocalDateTime windowStart,
                                    @Param("windowEnd") LocalDateTime windowEnd);
}