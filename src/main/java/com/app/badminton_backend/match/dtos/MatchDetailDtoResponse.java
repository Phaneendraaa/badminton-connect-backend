package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Typed response for GET /match-play/{matchId}.
 * Replaces the raw Map.of("match", ..., "sets", ..., "players", ...) to guarantee
 * a stable, documented JSON structure.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchDetailDtoResponse {
    private Match match;
    private List<MatchSet> sets;
    private List<MatchPlayerDto> players;
}
