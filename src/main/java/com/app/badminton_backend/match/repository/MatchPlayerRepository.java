package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchPlayer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, UUID> {
}