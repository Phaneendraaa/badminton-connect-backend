package com.app.badminton_backend.match.controller;

import com.app.badminton_backend.match.dtos.AssignTeamsDtoRequest;
import com.app.badminton_backend.match.dtos.MatchDetailDtoResponse;
import com.app.badminton_backend.match.dtos.MatchSetDtoRequest;
import com.app.badminton_backend.match.service.MatchPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/match-play")
@RequiredArgsConstructor
public class MatchPlayController {

    private final MatchPlayService matchPlayService;

    /**
     * Returns the full match state: metadata, sets, and player list.
     */
    @GetMapping("/{matchId}")
    public ResponseEntity<MatchDetailDtoResponse> getMatchData(@PathVariable UUID matchId) {
        return ResponseEntity.ok(matchPlayService.getMatchDetail(matchId));
    }

    /**
     * Organizer assigns players to Team A / Team B (and optionally renames the teams).
     * Must be called before startMatch(). Match must be in CREATED status.
     */
    @PostMapping("/{matchId}/assign-teams")
    public ResponseEntity<?> assignTeams(
            @PathVariable UUID matchId,
            @RequestBody AssignTeamsDtoRequest request) {
        matchPlayService.assignTeams(matchId, request);
        return ResponseEntity.ok(Map.of("message", "Teams assigned successfully"));
    }

    /**
     * Organizer starts the match (status CREATED → PLAYING).
     * Requires all players to have been assigned (no UNASSIGNED entries).
     */
    @PostMapping("/{matchId}/start")
    public ResponseEntity<?> startMatch(@PathVariable UUID matchId) {
        matchPlayService.startMatch(matchId);
        return ResponseEntity.ok(Map.of("message", "Match started"));
    }

    @PostMapping("/{matchId}/set")
    public ResponseEntity<?> addMatchSet(@PathVariable UUID matchId, @RequestBody MatchSetDtoRequest request) {
        matchPlayService.addMatchSet(matchId, request);
        return ResponseEntity.ok(Map.of("message", "Set updated"));
    }

    @PostMapping("/{matchId}/finish")
    public ResponseEntity<?> finishMatch(@PathVariable UUID matchId) {
        matchPlayService.finishMatch(matchId);
        return ResponseEntity.ok(Map.of("message", "Match finished"));
    }

    @DeleteMapping("/{matchId}/set/{setNumber}")
    public ResponseEntity<?> deleteMatchSet(@PathVariable UUID matchId, @PathVariable Integer setNumber) {
        matchPlayService.deleteMatchSet(matchId, setNumber);
        return ResponseEntity.ok(Map.of("message", "Set deleted successfully"));
    }

    /**
     * Organizer removes a confirmed player from the match.
     *
     * Body: { "userId": "<UUID>", "reason": "optional string" }
     *
     * Side effects (enforced in service):
     *  - MatchPlayer row deleted; audit log written.
     *  - Slot freed; post reopened to OPEN if it was FULL.
     *  - PLAYER_REMOVED in-app notification sent to the removed player.
     *  - STOMP broadcast on /topic/match/{matchId} so the removed player's
     *    client can navigate away immediately.
     */
    @PostMapping("/{matchId}/remove-player")
    public ResponseEntity<?> removePlayer(
            @PathVariable UUID matchId,
            @RequestBody Map<String, String> body) {
        UUID targetUserId = UUID.fromString(body.get("userId"));
        String reason = body.getOrDefault("reason", null);
        matchPlayService.removePlayer(matchId, targetUserId, reason);
        return ResponseEntity.ok(Map.of("message", "Player removed successfully"));
    }
}
