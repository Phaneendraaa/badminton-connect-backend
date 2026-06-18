package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
}