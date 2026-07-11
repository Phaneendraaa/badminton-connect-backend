package com.app.badminton_backend.auth.service;

import com.app.badminton_backend.auth.dto.LoginRequestDto;
import com.app.badminton_backend.auth.dto.LoginResponseDto;
import com.app.badminton_backend.auth.dto.PhoneOtpDto;
import com.app.badminton_backend.auth.entity.Otp;
import com.app.badminton_backend.auth.entity.RefreshToken;
import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.entity.type.OtpType;
import com.app.badminton_backend.auth.entity.type.UserRole;
import com.app.badminton_backend.auth.otpService.SendOtpSMS;
import com.app.badminton_backend.auth.repository.OtpRepository;
import com.app.badminton_backend.auth.repository.RefreshTokenRepository;
import com.app.badminton_backend.auth.repository.UserRepository;
import com.app.badminton_backend.auth.util.AuthUtil;
import com.app.badminton_backend.exceptions.InvalidOtpException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class AuthService {

    // Refresh token validity: 7 days
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 7;

    private final UserRepository userRepository;
    private final SendOtpSMS sendOtpSMS;
    private final OtpRepository otpRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuthUtil authUtil;

    @Transactional
    public void loginSendOtp(@Valid LoginRequestDto loginRequestDto) {
        String phoneNumber = normalizePhoneNumber(loginRequestDto.getPhoneNumber());

        Otp otp = sendOtpSMS.sendOneTimePassword(phoneNumber, OtpType.LOGIN);
        otpRepository.save(otp);
    }

    @Transactional
    public LoginResponseDto verifyLoginOtp(@Valid PhoneOtpDto phoneOtpDto) {
        boolean isNewUser = false;
        String phoneNumber = normalizePhoneNumber(phoneOtpDto.getPhoneNumber());
        Otp otp = findValidOtp(phoneNumber, phoneOtpDto.getOtp(), OtpType.LOGIN, LocalDateTime.now());

        User user = userRepository.findByPhoneNumber(phoneNumber);
        if (user == null) {
            isNewUser=true;
            User newUser = User.builder()
                    .phoneNumber(phoneNumber)
                    .isPhoneVerified(true)
                    .role(UserRole.USER)
                    .build();
            user = userRepository.save(newUser);
        }

        otp.setVerified(true);

        String accessToken  = authUtil.generateAccessToken(user);
        String refreshToken = createAndSaveRefreshToken(user);

        return new LoginResponseDto(accessToken, refreshToken, user.getId(),isNewUser);
    }

    /**
     * Validates the incoming refresh token, revokes it, issues a new access token
     * and a rotated refresh token (one-time-use pattern).
     */
    @Transactional
    public LoginResponseDto refreshTokens(String incomingToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(incomingToken)
                .orElseThrow(() -> new InvalidOtpException("Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new InvalidOtpException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOtpException("Refresh token has expired, please log in again");
        }

        // Revoke the used token (rotation — one-time-use)
        stored.setRevoked(true);

        User user = stored.getUser();
        String newAccessToken  = authUtil.generateAccessToken(user);
        String newRefreshToken = createAndSaveRefreshToken(user);

        return new LoginResponseDto(newAccessToken, newRefreshToken, user.getId(),false);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private String createAndSaveRefreshToken(User user) {
        String tokenValue = authUtil.generateRefreshToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenValue)
                .user(user)
                .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXPIRY_DAYS))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }

    private Otp findValidOtp(String phoneNumber, String otpValue, OtpType otpType,
                             LocalDateTime timeOfSubmit) {
        List<Otp> otpList = otpRepository.findAllByPhoneNumberAndOtpType(phoneNumber, otpType);
        for (Otp candidate : otpList) {
            if (candidate.getOtp().equals(otpValue)
                    && timeOfSubmit.isBefore(candidate.getExpiresAt())
                    && !candidate.isVerified()) {
                return candidate;
            }
        }
        throw new InvalidOtpException("Invalid or expired OTP");
    }

    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.startsWith("+91") ? phoneNumber : "+91" + phoneNumber;
    }
}
