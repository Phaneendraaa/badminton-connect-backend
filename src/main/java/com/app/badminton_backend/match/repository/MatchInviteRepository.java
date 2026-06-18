package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.entity.MatchSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public interface MatchInviteRepository extends JpaRepository<MatchInvite, UUID> {
    List<MatchInvite> findByMatchId(UUID matchId);
}