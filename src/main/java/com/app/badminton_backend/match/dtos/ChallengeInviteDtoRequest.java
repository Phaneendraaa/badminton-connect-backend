package com.app.badminton_backend.match.dtos;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChallengeInviteDtoRequest {
    public String matchId;
    public String phoneNumber;
}
