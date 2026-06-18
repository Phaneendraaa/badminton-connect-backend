package com.app.badminton_backend.auth.schedulers;

import com.app.badminton_backend.auth.repository.OtpRepository;
import com.app.badminton_backend.auth.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@AllArgsConstructor
public class CleanupScheduler {

    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanUpExpiredTokens() {
        otpRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
        refreshTokenRepository.deleteAllByExpiresAtBefore(LocalDateTime.now());
    }
}
