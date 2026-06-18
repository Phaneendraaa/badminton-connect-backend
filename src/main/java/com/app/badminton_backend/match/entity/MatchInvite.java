package com.app.badminton_backend.match.entity;

import com.app.badminton_backend.match.enums.InviteStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchInvite {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOrganizer = false; // true only for the organizer's own row

    @Column(nullable = false)
    private UUID userId; // invited user

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private InviteStatus status = InviteStatus.INVITED; // INVITED, JOINED, DECLINED

    @Builder.Default
    private LocalDateTime invitedAt = LocalDateTime.now();

    public boolean isOrganizer() {
        return isOrganizer;
    }
}
