package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.ChatMessageDtoResponse;
import com.app.badminton_backend.match.entity.MatchChatMessage;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.repository.MatchChatMessageRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchChatService {

    private final CurrentUserService currentUserService;
    private final MatchChatMessageRepository chatMessageRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final MatchRepository matchRepository;
    private final ProfileRepository profileRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Sends a chat message.
     *
     * Authorization:
     *  The sender must currently be a MatchPlayer for this match OR be the organizer.
     *  A user who left the match has their MatchPlayer row deleted, so this check
     *  will correctly return 403 — they lose the ability to send new messages.
     *
     * Persistence before broadcast:
     *  The message is persisted FIRST, then broadcast. This guarantees the message
     *  survives a client reconnect — the history endpoint will always return it.
     */
    @Transactional
    public ChatMessageDtoResponse sendMessage(UUID matchId, UUID senderId, String content) {
        assertIsParticipant(matchId, senderId);

        MatchChatMessage message = chatMessageRepository.save(MatchChatMessage.builder()
                .matchId(matchId)
                .senderId(senderId)
                .content(content)
                .build());

        ChatMessageDtoResponse response = toResponse(message);

        // Broadcast to all subscribers of this match's topic
        messagingTemplate.convertAndSend("/topic/match/" + matchId, response);

        return response;
    }

    /**
     * Fetches paginated chat history for a match.
     *
     * Authorization: same participant check as sendMessage.
     * A user who has left the match cannot fetch new history (their MatchPlayer
     * row is gone). This is documented in the entity comment on MatchChatMessage.
     */
    public Page<ChatMessageDtoResponse> getHistory(UUID matchId, int page, int size) {
        UUID userId = currentUserService.getCurrentUser().getId();
        assertIsParticipant(matchId, userId);

        Page<MatchChatMessage> messages = chatMessageRepository
                .findByMatchIdOrderBySentAtAsc(matchId, PageRequest.of(page, size));

        return messages.map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Verifies the given userId is an active MatchPlayer for this match
     * OR is the organizer of the companion Match row.
     *
     * Uses MatchPlayer (not MatchInvite/MatchJoinRequest) as the source of
     * truth — this covers both friend-challenge matches (where MatchPlayer rows
     * are created by assignTeams) and open-post matches (where they're created
     * on join-request accept). Both origins get chat.
     */
    private void assertIsParticipant(UUID matchId, UUID userId) {
        // Check MatchPlayer rows
        List<MatchPlayer> players = matchPlayerRepository.findByMatchId(matchId);
        boolean isPlayer = players.stream().anyMatch(p -> p.getUserId().equals(userId));

        if (isPlayer) return;

        // Also allow the organizer even before MatchPlayer rows exist (e.g. just created room)
        var match = matchRepository.findById(matchId).orElse(null);
        if (match != null && match.getOrganizerId().equals(userId)) return;

        throw new UnauthorizedActionException(
                "You are not a participant of this match and cannot access its chat");
    }

    private ChatMessageDtoResponse toResponse(MatchChatMessage message) {
        Profile senderProfile = profileRepository.findById(message.getSenderId()).orElse(null);
        return ChatMessageDtoResponse.builder()
                .id(message.getId())
                .matchId(message.getMatchId())
                .senderId(message.getSenderId())
                .senderName(senderProfile != null
                        ? senderProfile.getFirstName() + " " + senderProfile.getLastName()
                        : "Unknown")
                .senderAvatarUrl(senderProfile != null ? senderProfile.getProfilePictureUrl() : null)
                .content(message.getContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
