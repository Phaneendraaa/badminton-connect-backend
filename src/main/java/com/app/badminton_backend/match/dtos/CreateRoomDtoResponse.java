package com.app.badminton_backend.match.dtos;


import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class CreateRoomDtoResponse {
    UUID matchId;
}
