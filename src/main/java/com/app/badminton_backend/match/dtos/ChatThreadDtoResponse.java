package com.app.badminton_backend.match.dtos;

import com.app.badminton_backend.match.enums.MatchType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single chat thread in the Messages inbox.
 *
 * Each thread is one Match the current user participates in.
 * The frontend uses matchId to open the existing MatchChat screen.
 */
@Data
@Builder
public class ChatThreadDtoResponse {
    private UUID matchId;
    private String matchName;
    private MatchType matchType;
    private LocalDateTime scheduledAt;
    private String lastMessage;        // content of the most recent message, or null if no messages yet
    private LocalDateTime lastMessageAt;
    private int unreadCount;           // messages in the last 24h (pragmatic approximation until read-state tracking exists)
    private List<String> participantNames; // other players' names (excluding current user)
}
