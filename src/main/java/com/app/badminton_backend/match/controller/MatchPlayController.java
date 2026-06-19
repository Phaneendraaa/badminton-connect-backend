package com.app.badminton_backend.match.controller;

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

    @GetMapping("/{matchId}")
    public ResponseEntity<?> getMatchData(@PathVariable UUID matchId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "match", matchPlayService.getMatch(matchId),
                    "sets", matchPlayService.getMatchSets(matchId),
                    "players", matchPlayService.getMatchPlayers(matchId)
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getClass().getName(),
                    "message", e.getMessage() != null ? e.getMessage() : "No message"
            ));
        }
    }

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
}
