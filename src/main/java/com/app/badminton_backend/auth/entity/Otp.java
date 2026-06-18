package com.app.badminton_backend.auth.entity;

import com.app.badminton_backend.auth.entity.type.OtpType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Builder
@ToString
@RequiredArgsConstructor
@AllArgsConstructor
public class Otp {

    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;


    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String otp;

    @Enumerated(value = EnumType.STRING)
    private OtpType otpType;

    private LocalDateTime expiresAt;

    private boolean isVerified=false;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
