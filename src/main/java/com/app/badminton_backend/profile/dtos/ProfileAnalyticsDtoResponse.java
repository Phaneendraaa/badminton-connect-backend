package com.app.badminton_backend.profile.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileAnalyticsDtoResponse {
    private int totalMatchesPlayed;
    private double winRate;
    private int currentElo;
    private int peakElo;
    private String currentStreak;
    private List<String> recentForm;
    private String mostPlayedMatchType;
    private String mostFrequentCity;
}
