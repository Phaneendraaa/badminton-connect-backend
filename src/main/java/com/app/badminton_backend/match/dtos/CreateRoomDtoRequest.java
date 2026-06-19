package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import lombok.Data;

@Data
public class CreateRoomDtoRequest {
    MatchType matchType;

    private String matchName;
    private String scheduledTime; // ISO-8601 string from frontend
}
