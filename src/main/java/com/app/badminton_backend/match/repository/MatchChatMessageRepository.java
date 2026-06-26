package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface MatchChatMessageRepository extends JpaRepository<MatchChatMessage, UUID> {

    /** Paginated history, oldest-first so the client can render a normal chat view. */
    Page<MatchChatMessage> findByMatchIdOrderBySentAtAsc(UUID matchId, Pageable pageable);

    /** Most recent message for a match — used to build thread previews. */
    Optional<MatchChatMessage> findTopByMatchIdOrderBySentAtDesc(UUID matchId);

    /** Count of messages after a timestamp — used as a pragmatic unread approximation. */
    long countByMatchIdAndSentAtAfter(UUID matchId, LocalDateTime since);
}

