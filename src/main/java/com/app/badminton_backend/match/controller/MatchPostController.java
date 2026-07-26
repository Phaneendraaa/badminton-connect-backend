package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.dtos.CreatePostDtoRequest;
import com.app.badminton_backend.match.dtos.MyPostDtoResponse;
import com.app.badminton_backend.match.dtos.PostDetailDtoResponse;
import com.app.badminton_backend.match.dtos.PostFeedItemDtoResponse;
import com.app.badminton_backend.match.entity.MatchPost;
import com.app.badminton_backend.match.service.MatchPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/match-post")
@RequiredArgsConstructor
public class MatchPostController {

    private final MatchPostService matchPostService;

    /**
     * Create a new open match post.
     * Simultaneously creates a companion Match row (origin=OPEN).
     */
    @PostMapping("/create")
    public ResponseEntity<?> createPost(@Valid @RequestBody CreatePostDtoRequest request) {
        MatchPost post = matchPostService.createPost(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "postId", post.getId(),
                        "matchId", post.getMatchId(),
                        "message", "Post created successfully"));
    }

    /**
     * Paginated feed of open posts the current user can join.
     *
     * Query params (all optional):
     *   matchType  — SINGLES | DOUBLES
     *   eloMin     — (unused server-side in v1; server uses caller's own ELO for the filter)
     *   eloMax     — same as above
     *   dateFrom   — ISO-8601 lower bound on scheduledAt
     *   dateTo     — ISO-8601 upper bound on scheduledAt
     *   location   — free-text substring match on the Maps URL/text
     *   city       — exact match on the curated city field (e.g. "Hyderabad")
     *   page       — 0-indexed (default 0)
     *   size       — page size (default 20)
     */
    @GetMapping("/feed")
    public ResponseEntity<Page<PostFeedItemDtoResponse>> getFeed(
            @RequestParam(required = false) String matchType,
            @RequestParam(required = false) Integer eloMin,
            @RequestParam(required = false) Integer eloMax,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<PostFeedItemDtoResponse> feed = matchPostService.getFeed(
                matchType, eloMin, eloMax, dateFrom, dateTo, location, city, page, size);
        return ResponseEntity.ok(feed);
    }

    /**
     * Full post detail: post info + organizer profile/ELO + confirmed roster.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostDetailDtoResponse> getPostDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(matchPostService.getPostDetail(id));
    }

    /**
     * Organizer cancels their post. Auto-rejects all pending join requests.
     * Only allowed while status is OPEN or FULL.
     */
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<?> cancelPost(@PathVariable UUID id) {
        matchPostService.cancelPost(id);
        return ResponseEntity.ok(Map.of("message", "Post cancelled successfully"));
    }

    /**
     * Returns all posts created by the current authenticated user (organizer view).
     * Includes all statuses (OPEN, FULL, CANCELLED, EXPIRED) and a pendingRequestCount
     * per post for the My Posts tab badge.
     *
     * Used by the Activity → My Posts tab.
     */
    @GetMapping("/mine")
    public ResponseEntity<List<MyPostDtoResponse>> getMyPosts() {
        return ResponseEntity.ok(matchPostService.getMyPosts());
    }

    /**
     * Organizer extends the scheduled time of a FULL (confirmed) post.
     *
     * Body: { "newScheduledAt": "2026-08-01T18:00:00", "newExpiresAt": "2026-08-01T20:00:00" }
     *
     * Validation (enforced in service):
     *  - Caller must be the post creator.
     *  - Post must be FULL.
     *  - newScheduledAt must be in the future.
     *  - newExpiresAt must be after newScheduledAt.
     *  - newExpiresAt ≤ currentExpiresAt + 24h (extension cap per call).
     */
    @PostMapping("/{id}/extend")
    public ResponseEntity<?> extendPost(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        LocalDateTime newScheduledAt = LocalDateTime.parse(
                body.get("newScheduledAt"), DateTimeFormatter.ISO_DATE_TIME);
        LocalDateTime newExpiresAt = LocalDateTime.parse(
                body.get("newExpiresAt"), DateTimeFormatter.ISO_DATE_TIME);
        matchPostService.extendPost(id, newScheduledAt, newExpiresAt);
        return ResponseEntity.ok(Map.of("message", "Play time extended successfully"));
    }
}
