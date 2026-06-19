package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.enums.MatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchHistoryDtoResponse {
    private UUID matchId;
    private String matchName;
    private MatchType matchType;
    private MatchStatus status;
    private LocalDateTime playedAt;
    private LocalDateTime scheduledAt;
    private Team winnerTeam;
    private int teamASetWins;
    private int teamBSetWins;
    private Integer myEloChange; // Optional: ELO change for the current user
}
