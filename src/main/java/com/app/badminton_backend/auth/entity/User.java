package com.app.badminton_backend.auth.entity;


import com.app.badminton_backend.auth.entity.type.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;
@Entity
@Data
@ToString
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Table(
        indexes = {
                @Index(
                        name = "phone_number_id",
                        columnList = "phone_number"
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    @Builder.Default
    private boolean isPhoneVerified=false;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @UpdateTimestamp
    private LocalDateTime updatedAt;


}
