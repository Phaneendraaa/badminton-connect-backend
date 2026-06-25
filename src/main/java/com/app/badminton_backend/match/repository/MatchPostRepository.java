package com.app.badminton_backend.match.repository;

import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MatchPostRepository extends JpaRepository<MatchPost, UUID> {

    List<MatchPost> findByCreatorId(UUID creatorId);

    List<MatchPost> findByStatus(PostStatus status);

    /** All posts created by this user, newest first. Used by GET /match-post/mine. */
    List<MatchPost> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    /**
     * Paginated feed query.
     *
     * Exclusion rules (hard gates, not soft sort):
     *  1. Exclude the requesting user's own posts (creatorId != requesterId).
     *  2. Exclude non-OPEN posts.
     *  3. Exclude posts that have already passed their scheduledAt.
     *  4. Optional match-type filter.
     *  5. Optional ELO overlap: post's range [eloMin, eloMax] must overlap
     *     with the user's ELO (userElo >= post.eloMin AND userElo <= post.eloMax).
     *  6. Optional date-range filter on scheduledAt.
     *  7. Optional free-text location substring match.
     *  8. Optional city exact-match filter (curated city name or "Other").
     *
     * Exclusion of posts the user already has an ACCEPTED request for is
     * handled in MatchPostService by filtering the returned page client-side
     * (avoids a complex NOT-IN subquery while request counts are small).
     */
    @Query("""
            SELECT p FROM MatchPost p
            WHERE p.creatorId <> :requesterId
              AND p.status = 'OPEN'
              AND p.expiresAt > :now
              AND (:matchType IS NULL OR p.matchType = :matchType)
              AND (:userElo IS NULL OR (p.eloMin <= :userElo AND p.eloMax >= :userElo))
              AND (:dateFrom IS NULL OR p.scheduledAt >= :dateFrom)
              AND (:dateTo IS NULL OR p.scheduledAt <= :dateTo)
              AND (:location IS NULL OR LOWER(p.location) LIKE LOWER(CONCAT('%', :location, '%')))
              AND (:city IS NULL OR p.city = :city)
            ORDER BY p.scheduledAt ASC
            """)
    Page<MatchPost> findFeed(
            @Param("requesterId") UUID requesterId,
            @Param("now") LocalDateTime now,
            @Param("matchType") String matchType,
            @Param("userElo") Integer userElo,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("location") String location,
            @Param("city") String city,
            Pageable pageable
    );

    /**
     * Count of PENDING join requests per post — used by getMyPosts().
     * Returns a list of Object[] where [0]=postId (UUID) and [1]=count (Long).
     * Aggregated server-side to avoid N+1 queries in the organizer feed.
     */
    @Query("""
            SELECT r.postId, COUNT(r)
            FROM MatchJoinRequest r
            WHERE r.postId IN :postIds
              AND r.status = 'PENDING'
            GROUP BY r.postId
            """)
    List<Object[]> countPendingRequestsForPosts(@Param("postIds") List<UUID> postIds);

    /** Used by the expiry scheduler to batch-expire posts whose window has passed. */
    @Query("SELECT p FROM MatchPost p WHERE p.status = 'OPEN' AND p.expiresAt <= :now")
    List<MatchPost> findExpiredOpenPosts(@Param("now") LocalDateTime now);
}
