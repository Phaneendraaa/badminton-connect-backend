package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.InviteStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoomPlayerDto {
    private UUID userId;
    private String name;
    private String phoneNumber;
    private String profilePictureUrl;
    private InviteStatus inviteStatus;
    private Boolean isOrganizer;
}
