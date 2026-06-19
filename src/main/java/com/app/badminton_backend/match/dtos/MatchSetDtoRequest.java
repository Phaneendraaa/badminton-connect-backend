package com.app.badminton_backend.match.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchSetDtoRequest {
    private Integer setNumber;
    private Integer teamAScore;
    private Integer teamBScore;
}
