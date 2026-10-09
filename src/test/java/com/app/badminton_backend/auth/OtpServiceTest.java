package com.app.badminton_backend.auth;

import com.app.badminton_backend.auth.config.TwilioConfig;
import com.app.badminton_backend.auth.entity.Otp;
import com.app.badminton_backend.auth.entity.type.OtpType;
import com.app.badminton_backend.auth.otpService.OtpGenerator;
import com.app.badminton_backend.auth.otpService.SendOtpSMS;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtpServiceTest {

    @Test
    void otpGenerator_alwaysProducesSixDigitNumbers() {
        OtpGenerator generator = new OtpGenerator();

        for (int i = 0; i < 2000; i++) {
            String otp = generator.generateOtp();
            assertTrue(otp.matches("^[1-9]\\d{5}$"), "OTP must be exactly 6 digits, got: " + otp);
            int value = Integer.parseInt(otp);
            assertTrue(value >= 100_000 && value <= 999_999);
        }
    }

    @Test
    void otpGenerator_doesNotReturnTheSameValueEveryTime() {
        OtpGenerator generator = new OtpGenerator();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(generator.generateOtp());
        }
        assertTrue(seen.size() > 100, "200 draws from 900k values should be mostly distinct");
    }

    @Test
    void sendOtpSms_buildsUnverifiedOtp_expiringIn90Seconds() {
        OtpGenerator generator = mock(OtpGenerator.class);
        when(generator.generateOtp()).thenReturn("654321");
        SendOtpSMS sms = new SendOtpSMS(mock(TwilioConfig.class), generator);

        LocalDateTime before = LocalDateTime.now();
        Otp otp = sms.sendOneTimePassword("+919876543210", OtpType.LOGIN);

        verify(generator).generateOtp();
        assertNotNull(otp.getOtp());
        assertEquals("+919876543210", otp.getPhoneNumber());
        assertEquals(OtpType.LOGIN, otp.getOtpType());
        assertFalse(otp.isVerified());

        long secondsUntilExpiry = ChronoUnit.SECONDS.between(before, otp.getExpiresAt());
        assertTrue(secondsUntilExpiry >= 89 && secondsUntilExpiry <= 91,
                "OTP should expire ~90s after creation, was " + secondsUntilExpiry);
    }

    @Test
    void sendOtpSms_normalisesPhoneNumberWithoutCountryCode() {
        SendOtpSMS sms = new SendOtpSMS(mock(TwilioConfig.class), new OtpGenerator());

        Otp otp = sms.sendOneTimePassword("9876543210", OtpType.LOGIN);

        assertEquals("+919876543210", otp.getPhoneNumber());
    }
}
