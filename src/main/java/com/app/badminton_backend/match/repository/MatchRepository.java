package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    List<Match> findByOrganizerId(UUID organizerId);

    /** Used by getUnifiedMyRooms() to look up OPEN-origin matches by their postId. */
    List<Match> findByPostIdIn(List<UUID> postIds);
}