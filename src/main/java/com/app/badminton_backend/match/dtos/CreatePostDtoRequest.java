package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for POST /match-post/create.
 * scheduledAt is sent as an ISO-8601 string so the frontend doesn't need
 * to serialise a Java type — same convention as CreateRoomDtoRequest.
 */
@Data
public class CreatePostDtoRequest {

    @NotBlank(message = "Match title is required")
    @Size(max = 100, message = "Match title cannot exceed 100 characters")
    private String title;

    @NotNull(message = "Match type is required")
    private MatchType matchType;

    @Pattern(regexp = "^(http://|https://).*$", message = "Location must be a valid URL starting with http:// or https://")
    private String location;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    /**
     * ISO-8601 datetime string, e.g. "2026-07-01T15:00:00".
     * Must represent a moment in the future (validated in service).
     */
    @NotBlank(message = "Scheduled time is required")
    private String scheduledAt;

    @NotNull(message = "Minimum ELO is required")
    @Min(value = 0, message = "ELO minimum cannot be negative")
    private Integer eloMin;

    @NotNull(message = "Maximum ELO is required")
    @Min(value = 0, message = "ELO maximum cannot be negative")
    private Integer eloMax;
}
