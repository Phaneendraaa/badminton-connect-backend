package com.app.badminton_backend.auth.otpService;

import com.app.badminton_backend.auth.config.TwilioConfig;
import com.app.badminton_backend.auth.entity.Otp;
import com.app.badminton_backend.auth.entity.type.OtpType;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
@Service
@AllArgsConstructor
public class SendOtpSMS {

    private static final int OTP_EXPIRY_SECONDS = 90;

    private final TwilioConfig twilioConfig;
    private final OtpGenerator otpGenerator;
    public Otp sendOneTimePassword(String phoneNumber, OtpType otpType) {
        System.out.println("triggered otp "+phoneNumber);
        String normalizedPhone = normalizePhoneNumber(phoneNumber);
        String otpValue = otpGenerator.generateOtp();
        String text = "Your Badminton app verification code is: " + otpValue;

//        Message message = Message.creator(
//                        new PhoneNumber(normalizedPhone),
//                        new PhoneNumber(twilioConfig.getTrialNumber()),
//                        text)
//                .create();
//        if (message.getSid() == null) {
//            throw new RuntimeException("Failed to send OTP");
//        }

        return createOtp("123456", normalizedPhone, otpType);
    }

    private Otp createOtp(String otp, String phoneNumber, OtpType otpType) {
        return Otp.builder()
                .phoneNumber(phoneNumber)
                .otp(otp)
                .otpType(otpType)
                .expiresAt(LocalDateTime.now().plusSeconds(OTP_EXPIRY_SECONDS))
                .isVerified(false)
                .build();
    }
    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.startsWith("+91") ? phoneNumber : "+91" + phoneNumber;
    }
}





