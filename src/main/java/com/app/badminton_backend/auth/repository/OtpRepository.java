package com.app.badminton_backend.auth.repository;

import com.app.badminton_backend.auth.entity.Otp;
import com.app.badminton_backend.auth.entity.type.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OtpRepository extends JpaRepository<Otp, UUID> {

    List<Otp> findAllByPhoneNumberAndOtpType(String phoneNumber, OtpType otpType);

    void deleteAllByExpiresAtBefore(LocalDateTime time);
}
