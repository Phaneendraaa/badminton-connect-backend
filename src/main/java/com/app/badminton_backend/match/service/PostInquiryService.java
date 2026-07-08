package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.exceptions.PostNotFoundException;
import com.app.badminton_backend.exceptions.UnauthorizedActionException;
import com.app.badminton_backend.match.entity.MatchJoinRequest;
import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.entity.PostInquiryMessage;
import com.app.badminton_backend.match.enums.JoinRequestStatus;
import com.app.badminton_backend.match.repository.MatchJoinRequestRepository;
import com.app.badminton_backend.match.repository.MatchPostRepository;
import com.app.badminton_backend.match.repository.PostInquiryMessageRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PostInquiryService {

    private final CurrentUserService currentUserService;
    private final MatchPostRepository matchPostRepository;
    private final MatchJoinRequestRepository matchJoinRequestRepository;
    private final PostInquiryMessageRepository inquiryMessageRepository;
    private final ProfileRepository profileRepository;

    /**
     * DTO for a single message in the thread.
     */
    public record InquiryMessageDto(
            UUID id,
            UUID postId,
            UUID senderId,
            String senderName,
            String senderAvatarUrl,
            String content,
            java.time.LocalDateTime sentAt
    ) {}

    // -------------------------------------------------------------------------
    // GET THREAD
    // -------------------------------------------------------------------------

    /**
     * Returns all inquiry messages for a post, in chronological order.
     *
     * Access rules:
     *  - The post creator (organizer) can always view the thread.
     *  - Any user who has any join request (PENDING, ACCEPTED, REJECTED, or CANCELLED) can view.
     *  - Anyone else gets UnauthorizedException.
     */
    public List<InquiryMessageDto> getThread(UUID postId) {
        UUID callerId = currentUserService.getCurrentUser().getId();

        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        assertParticipant(post, callerId, postId);

        List<PostInquiryMessage> messages = inquiryMessageRepository.findByPostIdOrderBySentAtAsc(postId);
        return messages.stream().map(m -> toDto(m)).toList();
    }

    // -------------------------------------------------------------------------
    // SEND MESSAGE
    // -------------------------------------------------------------------------

    /**
     * Sends a message to the inquiry thread for a post.
     *
     * Access rules (same as getThread):
     *  - Post creator may always message.
     *  - Anyone who has a join request for this post may message.
     * Content is trimmed and must be non-blank.
     */
    @Transactional
    public InquiryMessageDto sendMessage(UUID postId, String content) {
        UUID callerId = currentUserService.getCurrentUser().getId();

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("Message content exceeds 1000 characters");
        }

        MatchPost post = matchPostRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found: " + postId));

        assertParticipant(post, callerId, postId);

        PostInquiryMessage saved = inquiryMessageRepository.save(
                PostInquiryMessage.builder()
                        .postId(postId)
                        .senderId(callerId)
                        .content(content.trim())
                        .build());

        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Ensures callerId is either the post creator OR has any join request for this post.
     * Throws UnauthorizedActionException otherwise.
     */
    private void assertParticipant(MatchPost post, UUID callerId, UUID postId) {
        if (post.getCreatorId().equals(callerId)) return; // organizer always allowed

        boolean hasRequest = matchJoinRequestRepository
                .findByPostIdAndUserId(postId, callerId)
                .isPresent();

        if (!hasRequest) {
            throw new UnauthorizedActionException(
                    "Only the post organizer or users with a join request can access the inquiry thread");
        }
    }

    private InquiryMessageDto toDto(PostInquiryMessage m) {
        Profile senderProfile = profileRepository.findById(m.getSenderId()).orElse(null);
        String senderName = senderProfile != null
                ? senderProfile.getFirstName() + " " + senderProfile.getLastName()
                : "Unknown";
        String avatarUrl = senderProfile != null ? senderProfile.getProfilePictureUrl() : null;

        return new InquiryMessageDto(
                m.getId(),
                m.getPostId(),
                m.getSenderId(),
                senderName,
                avatarUrl,
                m.getContent(),
                m.getSentAt()
        );
    }
}
