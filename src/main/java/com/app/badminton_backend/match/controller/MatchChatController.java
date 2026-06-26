package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.match.dtos.ChatMessageDtoResponse;
import com.app.badminton_backend.match.dtos.ChatThreadDtoResponse;
import com.app.badminton_backend.match.dtos.SendMessageDtoRequest;
import com.app.badminton_backend.match.service.MatchChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MatchChatController {

    private final MatchChatService matchChatService;
    private final CurrentUserService currentUserService;

    /**
     * REST endpoint for paginated chat history.
     *
     * Authorization is checked inside matchChatService.getHistory():
     * only current MatchPlayers (or the organizer) can read history.
     *
     * Query params:
     *   page — 0-indexed (default 0)
     *   size — page size (default 50)
     */
    @GetMapping("/match-chat/{matchId}/messages")
    public ResponseEntity<Page<ChatMessageDtoResponse>> getHistory(
            @PathVariable UUID matchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(matchChatService.getHistory(matchId, page, size));
    }

    /**
     * GET /match-chat/threads
     * Returns all active match chat threads for the current user.
     * Each thread is a match the user participates in, with the latest
     * message preview and an unread count.
     *
     * Used by Messages.js to render the inbox.
     */
    @GetMapping("/match-chat/threads")
    public ResponseEntity<List<ChatThreadDtoResponse>> getThreads() {
        return ResponseEntity.ok(matchChatService.getThreads());
    }

    /**
     * STOMP endpoint for real-time message sending.
     * Client sends to: /app/chat.send
     * Message is persisted then broadcast to: /topic/match/{matchId}
     *
     * The Principal is set by WebSocketAuthInterceptor during CONNECT.
     * If the principal is null (unauthenticated connection), getCurrentUser()
     * inside MatchChatService will throw — the client receives a STOMP ERROR frame.
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload SendMessageDtoRequest request, Principal principal) {
        UUID senderId = currentUserService.getCurrentUser().getId();
        matchChatService.sendMessage(request.getMatchId(), senderId, request.getContent());
    }
}
