package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchChatMessageRepository extends JpaRepository<MatchChatMessage, UUID> {

    /** Paginated history, oldest-first so the client can render a normal chat view. */
    Page<MatchChatMessage> findByMatchIdOrderBySentAtAsc(UUID matchId, Pageable pageable);
}
