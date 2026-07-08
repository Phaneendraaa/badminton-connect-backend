package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchJoinRequest;
import com.app.badminton_backend.match.enums.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchJoinRequestRepository extends JpaRepository<MatchJoinRequest, UUID> {

    List<MatchJoinRequest> findByPostId(UUID postId);

    List<MatchJoinRequest> findByPostIdAndStatus(UUID postId, JoinRequestStatus status);

    long countByPostIdAndStatus(UUID postId, JoinRequestStatus status);

    List<MatchJoinRequest> findByUserId(UUID userId);

    Optional<MatchJoinRequest> findByPostIdAndUserId(UUID postId, UUID userId);

    List<MatchJoinRequest> findByMatchId(UUID matchId);

    /** All requests across all posts where the given user is the creator. */
    @Query("""
            SELECT r FROM MatchJoinRequest r
            JOIN MatchPost p ON r.postId = p.id
            WHERE p.creatorId = :organizerId
            ORDER BY r.createdAt DESC
            """)
    List<MatchJoinRequest> findRequestsForPostsOwnedBy(@Param("organizerId") UUID organizerId);

    /**
     * Bulk-rejects all still-PENDING requests for a post — called when
     * the post fills up or is cancelled. Single UPDATE avoids N individual
     * saves and keeps the transaction short.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE MatchJoinRequest r
            SET r.status = 'REJECTED', r.respondedAt = CURRENT_TIMESTAMP
            WHERE r.postId = :postId AND r.status = 'PENDING'
            """)
    void rejectAllPendingForPost(@Param("postId") UUID postId);
}
