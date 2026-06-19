package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
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
public class MyRequestDtoResponse {
    private UUID matchId;
    private MatchType matchType;
    private String organizerName;
    private LocalDateTime invitedAt;
    private String matchName;
    private LocalDateTime scheduledAt;
}
