package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRoomDtoRequest {
    @NotNull(message = "Match Type is required")
    private MatchType matchType;

    @NotBlank(message = "Match Name is required")
    private String matchName;

    private String scheduledTime; // ISO-8601 string
}
