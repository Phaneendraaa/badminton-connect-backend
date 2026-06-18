package com.app.badminton_backend.elo.repository;

import com.app.badminton_backend.elo.entity.EloPoints;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EloPointsRepository extends JpaRepository<EloPoints, UUID> {
}