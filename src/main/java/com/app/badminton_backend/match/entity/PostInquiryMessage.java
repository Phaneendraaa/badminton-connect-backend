package com.app.badminton_backend.match.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a message in a pre-join inquiry thread tied to an open post.
 *
 * This is separate from MatchChatMessage (which is match-scoped and requires
 * all players to be accepted). PostInquiryMessage allows interested players
 * to ask the organizer questions before submitting a join request, or after
 * submitting but before being accepted.
 *
 * Thread participants: the post creator (organizer) and any user who has
 * a join request (PENDING, ACCEPTED, or REJECTED) for this post.
 */
@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "post_inquiry_message")
public class PostInquiryMessage {

    @Id
    @GeneratedValue
    private UUID id;

    /** The open post this inquiry thread belongs to. */
    @Column(nullable = false)
    private UUID postId;

    /** The user who sent this message. */
    @Column(nullable = false)
    private UUID senderId;

    /** Message content — max 1000 characters. */
    @Column(nullable = false, length = 1000)
    private String content;

    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
