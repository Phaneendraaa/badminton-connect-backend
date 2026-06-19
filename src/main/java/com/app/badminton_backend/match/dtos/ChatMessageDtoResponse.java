package com.app.badminton_backend.match.dtos;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Outbound shape for both REST history and STOMP broadcast. */
@Data
@Builder
public class ChatMessageDtoResponse {

    private UUID id;
    private UUID matchId;
    private UUID senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private LocalDateTime sentAt;
}
