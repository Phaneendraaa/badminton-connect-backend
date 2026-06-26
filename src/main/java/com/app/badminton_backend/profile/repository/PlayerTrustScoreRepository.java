package com.app.badminton_backend.profile.repository;

import com.app.badminton_backend.profile.entity.PlayerTrustScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlayerTrustScoreRepository extends JpaRepository<PlayerTrustScore, UUID> {
}
