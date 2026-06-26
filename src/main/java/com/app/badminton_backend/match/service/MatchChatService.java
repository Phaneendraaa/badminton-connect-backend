package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.dtos.ChatMessageDtoResponse;
import com.app.badminton_backend.match.dtos.ChatThreadDtoResponse;
import com.app.badminton_backend.match.entity.MatchChatMessage;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.NotificationType;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final NotificationService notificationService;

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

        // Notify all other participants (push-style — they may not be actively subscribed)
        Profile senderProfile = profileRepository.findById(senderId).orElse(null);
        String senderName = senderProfile != null
                ? senderProfile.getFirstName() + " " + senderProfile.getLastName() : "Someone";
        var match = matchRepository.findById(matchId).orElse(null);
        String matchName = match != null ? match.getMatchName() : "your match";

        List<MatchPlayer> allPlayers = matchPlayerRepository.findByMatchId(matchId);
        for (MatchPlayer mp : allPlayers) {
            if (!mp.getUserId().equals(senderId)) {
                notificationService.create(
                        mp.getUserId(),
                        NotificationType.NEW_CHAT_MESSAGE,
                        null,
                        matchId,
                        senderName + ": " + (content.length() > 60 ? content.substring(0, 60) + "…" : content));
            }
        }

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
     * Returns all match chat threads for the current user, sorted by most
     * recent message. Each thread corresponds to a match the user participates in.
     *
     * "Unread count" is approximated as messages sent in the last 24 hours —
     * a pragmatic starting point until a proper UserChatReadState table is built.
     */
    public List<ChatThreadDtoResponse> getThreads() {
        UUID currentUserId = currentUserService.getCurrentUser().getId();

        // Collect all matches the user is a MatchPlayer in
        List<MatchPlayer> myRows = matchPlayerRepository.findByUserId(currentUserId);
        Map<UUID, com.app.badminton_backend.match.entity.Match> matchMap = new LinkedHashMap<>();
        for (MatchPlayer mp : myRows) {
            matchRepository.findById(mp.getMatchId()).ifPresent(m -> matchMap.put(m.getId(), m));
        }
        // Also include matches where user is organizer but may not have a MatchPlayer row yet
        matchRepository.findByOrganizerId(currentUserId).forEach(m -> matchMap.putIfAbsent(m.getId(), m));

        LocalDateTime since24h = LocalDateTime.now().minusHours(24);

        List<ChatThreadDtoResponse> threads = new ArrayList<>();
        for (com.app.badminton_backend.match.entity.Match match : matchMap.values()) {
            // Skip completed/cancelled matches from the messages inbox
            if (match.getStatus() == com.app.badminton_backend.match.enums.MatchStatus.COMPLETED
                    || match.getStatus() == com.app.badminton_backend.match.enums.MatchStatus.CANCELLED) {
                continue;
            }

            // Last message preview
            var latestOpt = chatMessageRepository.findTopByMatchIdOrderBySentAtDesc(match.getId());
            String lastMessageContent = latestOpt.map(MatchChatMessage::getContent).orElse(null);
            LocalDateTime lastMessageAt = latestOpt.map(MatchChatMessage::getSentAt).orElse(null);

            // Unread approximation: messages in last 24h
            long unreadCount = chatMessageRepository.countByMatchIdAndSentAtAfter(match.getId(), since24h);

            // Other participants' names
            List<MatchPlayer> allPlayers = matchPlayerRepository.findByMatchId(match.getId());
            List<String> participantNames = allPlayers.stream()
                    .filter(mp -> !mp.getUserId().equals(currentUserId))
                    .map(mp -> {
                        Profile p = profileRepository.findById(mp.getUserId()).orElse(null);
                        return p != null ? p.getFirstName() + " " + p.getLastName() : "Unknown";
                    })
                    .collect(Collectors.toList());

            threads.add(ChatThreadDtoResponse.builder()
                    .matchId(match.getId())
                    .matchName(match.getMatchName())
                    .matchType(match.getMatchType())
                    .scheduledAt(match.getScheduledAt())
                    .lastMessage(lastMessageContent)
                    .lastMessageAt(lastMessageAt)
                    .unreadCount((int) unreadCount)
                    .participantNames(participantNames)
                    .build());
        }

        // Sort by most recent activity (lastMessageAt, falling back to match createdAt)
        threads.sort((a, b) -> {
            LocalDateTime timeA = a.getLastMessageAt() != null ? a.getLastMessageAt() :
                    matchMap.get(a.getMatchId()).getCreatedAt();
            LocalDateTime timeB = b.getLastMessageAt() != null ? b.getLastMessageAt() :
                    matchMap.get(b.getMatchId()).getCreatedAt();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA); // descending: most recent first
        });

        return threads;
    }

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
