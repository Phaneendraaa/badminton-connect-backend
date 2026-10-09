package com.app.badminton_backend.auth;

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
import com.app.badminton_backend.auth.service.AuthService;
import com.app.badminton_backend.auth.util.AuthUtil;
import com.app.badminton_backend.exceptions.InvalidOtpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final String PHONE = "+919876543210";
    private static final String OTP_VALUE = "482913";

    @Mock private UserRepository userRepository;
    @Mock private SendOtpSMS sendOtpSMS;
    @Mock private OtpRepository otpRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private AuthUtil authUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, sendOtpSMS, otpRepository, refreshTokenRepository, authUtil);
        when(authUtil.generateAccessToken(any(User.class))).thenReturn("access-jwt");
        when(authUtil.generateRefreshToken()).thenReturn("new-refresh-token");
    }

    private Otp otp(String value, LocalDateTime expiresAt, boolean verified) {
        return Otp.builder()
                .phoneNumber(PHONE)
                .otp(value)
                .otpType(OtpType.LOGIN)
                .expiresAt(expiresAt)
                .isVerified(verified)
                .build();
    }

    private PhoneOtpDto phoneOtp(String phone, String otpValue) {
        PhoneOtpDto dto = new PhoneOtpDto();
        dto.setPhoneNumber(phone);
        dto.setOtp(otpValue);
        return dto;
    }

    private void stubOtps(Otp... otps) {
        when(otpRepository.findAllByPhoneNumberAndOtpType(PHONE, OtpType.LOGIN)).thenReturn(List.of(otps));
    }

    // ── loginSendOtp ──────────────────────────────────────────────────────────

    @Test
    void loginSendOtp_prependsCountryCode_andPersistsGeneratedOtp() {
        Otp generated = otp(OTP_VALUE, LocalDateTime.now().plusSeconds(90), false);
        when(sendOtpSMS.sendOneTimePassword(PHONE, OtpType.LOGIN)).thenReturn(generated);

        authService.loginSendOtp(LoginRequestDto.builder().phoneNumber("9876543210").build());

        verify(sendOtpSMS).sendOneTimePassword(PHONE, OtpType.LOGIN);
        verify(otpRepository).save(generated);
    }

    @Test
    void loginSendOtp_keepsExistingCountryCode() {
        Otp generated = otp(OTP_VALUE, LocalDateTime.now().plusSeconds(90), false);
        when(sendOtpSMS.sendOneTimePassword(PHONE, OtpType.LOGIN)).thenReturn(generated);

        authService.loginSendOtp(LoginRequestDto.builder().phoneNumber(PHONE).build());

        verify(sendOtpSMS).sendOneTimePassword(PHONE, OtpType.LOGIN);
    }

    // ── verifyLoginOtp ────────────────────────────────────────────────────────

    @Test
    void verifyLoginOtp_createsNewVerifiedUser_andFlagsIsNewUser() {
        Otp valid = otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), false);
        stubOtps(valid);
        UUID newId = UUID.randomUUID();
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(null);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(newId);
            return u;
        });

        LoginResponseDto response = authService.verifyLoginOtp(phoneOtp("9876543210", OTP_VALUE));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals(PHONE, savedUser.getValue().getPhoneNumber());
        assertEquals(UserRole.USER, savedUser.getValue().getRole());
        assertTrue(savedUser.getValue().isPhoneVerified());

        assertTrue(response.isNewUser());
        assertEquals(newId, response.getUserId());
        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertTrue(valid.isVerified(), "the OTP must be consumed after a successful login");
    }

    @Test
    void verifyLoginOtp_existingUser_isNotRecreated() {
        Otp valid = otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), false);
        stubOtps(valid);
        User existing = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(existing);

        LoginResponseDto response = authService.verifyLoginOtp(phoneOtp(PHONE, OTP_VALUE));

        verify(userRepository, never()).save(any());
        assertFalse(response.isNewUser());
        assertEquals(existing.getId(), response.getUserId());
    }

    @Test
    void verifyLoginOtp_storesRefreshTokenValidForSevenDays() {
        stubOtps(otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), false));
        User existing = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(existing);

        authService.verifyLoginOtp(phoneOtp(PHONE, OTP_VALUE));

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        RefreshToken token = saved.getValue();
        assertEquals("new-refresh-token", token.getToken());
        assertEquals(existing, token.getUser());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now().plusDays(7).minusMinutes(1)));
        assertTrue(token.getExpiresAt().isBefore(LocalDateTime.now().plusDays(7).plusMinutes(1)));
    }

    @Test
    void verifyLoginOtp_rejectsWrongOtp() {
        stubOtps(otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), false));

        InvalidOtpException ex = assertThrows(InvalidOtpException.class,
                () -> authService.verifyLoginOtp(phoneOtp(PHONE, "000000")));

        assertEquals("Invalid or expired OTP", ex.getMessage());
        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void verifyLoginOtp_rejectsExpiredOtp() {
        stubOtps(otp(OTP_VALUE, LocalDateTime.now().minusSeconds(1), false));

        assertThrows(InvalidOtpException.class,
                () -> authService.verifyLoginOtp(phoneOtp(PHONE, OTP_VALUE)));
    }

    @Test
    void verifyLoginOtp_rejectsOtpThatWasAlreadyUsed() {
        stubOtps(otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), true));

        assertThrows(InvalidOtpException.class,
                () -> authService.verifyLoginOtp(phoneOtp(PHONE, OTP_VALUE)));
    }

    @Test
    void verifyLoginOtp_picksTheMatchingOtp_whenSeveralWereIssued() {
        Otp stale = otp("111111", LocalDateTime.now().minusMinutes(5), false);
        Otp used = otp("222222", LocalDateTime.now().plusSeconds(30), true);
        Otp current = otp(OTP_VALUE, LocalDateTime.now().plusSeconds(60), false);
        stubOtps(stale, used, current);
        User existing = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        when(userRepository.findByPhoneNumber(PHONE)).thenReturn(existing);

        authService.verifyLoginOtp(phoneOtp(PHONE, OTP_VALUE));

        assertTrue(current.isVerified());
        assertFalse(stale.isVerified());
    }

    // ── refreshTokens ─────────────────────────────────────────────────────────

    private RefreshToken storedToken(User user, LocalDateTime expiresAt, boolean revoked) {
        RefreshToken token = RefreshToken.builder()
                .token("old-refresh-token")
                .user(user)
                .expiresAt(expiresAt)
                .isRevoked(revoked)
                .build();
        when(refreshTokenRepository.findByToken("old-refresh-token")).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void refreshTokens_revokesOldToken_andIssuesRotatedPair() {
        User user = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        RefreshToken old = storedToken(user, LocalDateTime.now().plusDays(3), false);

        LoginResponseDto response = authService.refreshTokens("old-refresh-token");

        assertTrue(old.isRevoked(), "the used refresh token must be revoked (one-time use)");
        assertEquals("access-jwt", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertNotEquals("old-refresh-token", response.getRefreshToken());
        assertEquals(user.getId(), response.getUserId());
        assertFalse(response.isNewUser());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void refreshTokens_rejectsUnknownToken() {
        when(refreshTokenRepository.findByToken("nope")).thenReturn(Optional.empty());

        InvalidOtpException ex = assertThrows(InvalidOtpException.class,
                () -> authService.refreshTokens("nope"));

        assertEquals("Invalid refresh token", ex.getMessage());
    }

    @Test
    void refreshTokens_rejectsRevokedToken_andIssuesNothing() {
        User user = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        storedToken(user, LocalDateTime.now().plusDays(3), true);

        InvalidOtpException ex = assertThrows(InvalidOtpException.class,
                () -> authService.refreshTokens("old-refresh-token"));

        assertEquals("Refresh token has been revoked", ex.getMessage());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshTokens_rejectsExpiredToken() {
        User user = User.builder().id(UUID.randomUUID()).phoneNumber(PHONE).role(UserRole.USER).build();
        storedToken(user, LocalDateTime.now().minusMinutes(1), false);

        InvalidOtpException ex = assertThrows(InvalidOtpException.class,
                () -> authService.refreshTokens("old-refresh-token"));

        assertTrue(ex.getMessage().contains("expired"));
        verify(refreshTokenRepository, never()).save(any());
    }
}
