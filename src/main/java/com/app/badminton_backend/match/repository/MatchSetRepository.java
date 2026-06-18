package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchSetRepository extends JpaRepository<MatchSet, UUID> {
}
