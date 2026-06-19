package com.app.badminton_backend.match.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** Payload for the STOMP @MessageMapping("/chat.send") endpoint. */
@Data
public class SendMessageDtoRequest {

    @NotNull(message = "Match ID is required")
    private UUID matchId;

    @NotBlank(message = "Message content cannot be empty")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String content;
}
