package com.app.badminton_backend.auth.repository;

import com.app.badminton_backend.auth.entity.RefreshToken;
import com.app.badminton_backend.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    void deleteAllByUser(User user);

    void deleteAllByExpiresAtBefore(LocalDateTime time);
}
