package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.dtos.UnifiedRoomDtoResponse;
import com.app.badminton_backend.match.service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for aggregated match-level operations.
 *
 * Distinct from ChallengeFriendController (which owns the challenge-flow steps:
 * create-room, invite, accept-invite, etc.) — this controller owns cross-origin
 * aggregations that span both CHALLENGE and OPEN matches.
 */
@RestController
@RequestMapping("/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * Returns all matches the current user is confirmed in, unified across
     * both the friend-challenge and open-post flows.
     *
     * Aggregation strategy:
     *  1. Matches where the user is the organizer (any origin).
     *  2. CHALLENGE matches where the user has a JOINED invite (non-organizer).
     *  3. OPEN matches where the user has a MatchPlayer row (joined via accepted request).
     *
     * De-duplicated by matchId server-side. COMPLETED matches are excluded
     * (those belong in Match History). Sorted by scheduledAt ascending (soonest first).
     *
     * Used by the Activity → My Rooms tab.
     */
    @GetMapping("/my-rooms")
    public ResponseEntity<List<UnifiedRoomDtoResponse>> getMyRooms() {
        return ResponseEntity.ok(matchService.getUnifiedMyRooms());
    }
}
