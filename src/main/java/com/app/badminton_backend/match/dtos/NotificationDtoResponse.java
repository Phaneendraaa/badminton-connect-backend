package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationDtoResponse {
    private UUID id;
    private NotificationType type;
    private UUID relatedPostId;
    private UUID relatedMatchId;
    private String message;
    private Boolean read;
    private LocalDateTime createdAt;
}
