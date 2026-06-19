package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.Team;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchPlayerDto {
    private UUID userId;
    private String name;
    private String profilePictureUrl;
    private Team team;
    private Integer eloBefore;
    private Integer eloAfter;
    private Integer eloChange;
}
