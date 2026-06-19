package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.dtos.JoinRequestDtoResponse;
import com.app.badminton_backend.match.entity.MatchJoinRequest;
import com.app.badminton_backend.match.service.MatchJoinRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MatchJoinRequestController {

    private final MatchJoinRequestService matchJoinRequestService;

    /**
     * Create a PENDING join request for a post.
     * Mapped under /match-post/{id}/request so it reads naturally:
     * "request to join this post".
     */
    @PostMapping("/match-post/{id}/request")
    public ResponseEntity<?> requestToJoin(@PathVariable("id") UUID postId) {
        MatchJoinRequest request = matchJoinRequestService.requestToJoin(postId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "requestId", request.getId(),
                        "status", request.getStatus(),
                        "message", "Join request submitted successfully"));
    }

    /**
     * Organizer accepts a join request.
     * Creates a MatchPlayer row and may flip post to FULL / match to CREATED.
     */
    @PatchMapping("/match-join-request/{id}/accept")
    public ResponseEntity<?> acceptRequest(@PathVariable("id") UUID requestId) {
        matchJoinRequestService.acceptRequest(requestId);
        return ResponseEntity.ok(Map.of("message", "Join request accepted"));
    }

    /** Organizer rejects a PENDING request. */
    @PatchMapping("/match-join-request/{id}/reject")
    public ResponseEntity<?> rejectRequest(@PathVariable("id") UUID requestId) {
        matchJoinRequestService.rejectRequest(requestId);
        return ResponseEntity.ok(Map.of("message", "Join request rejected"));
    }

    /**
     * Requester cancels their own PENDING request.
     * Distinct from leaveMatch — only works while the request is still PENDING.
     */
    @PatchMapping("/match-join-request/{id}/cancel")
    public ResponseEntity<?> cancelRequest(@PathVariable("id") UUID requestId) {
        matchJoinRequestService.cancelRequest(requestId);
        return ResponseEntity.ok(Map.of("message", "Join request cancelled"));
    }

    /**
     * An ACCEPTED player leaves the match before it starts.
     * Decrements slotsJoined, removes MatchPlayer row, reopens post if it was FULL.
     */
    @PatchMapping("/match-join-request/{id}/leave")
    public ResponseEntity<?> leaveMatch(@PathVariable("id") UUID requestId) {
        matchJoinRequestService.leaveMatch(requestId);
        return ResponseEntity.ok(Map.of("message", "Left the match successfully"));
    }

    /** All join requests submitted by the current user. */
    @GetMapping("/match-join-request/mine")
    public ResponseEntity<List<JoinRequestDtoResponse>> getMyJoinRequests() {
        return ResponseEntity.ok(matchJoinRequestService.getMyJoinRequests());
    }

    /**
     * All join requests submitted to posts created by the current user.
     * The organizer uses this to see who wants to join their posts.
     */
    @GetMapping("/match-join-request/for-my-posts")
    public ResponseEntity<List<JoinRequestDtoResponse>> getRequestsForMyPosts() {
        return ResponseEntity.ok(matchJoinRequestService.getRequestsForMyPosts());
    }
}
