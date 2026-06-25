package com.app.badminton_backend.profile.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileStatsDtoResponse {
    private int matchesPlayed;
    private double winRate;
    private int trustScore;
}
