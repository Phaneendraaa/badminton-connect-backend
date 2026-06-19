package com.app.badminton_backend.match.controller;


import com.app.badminton_backend.match.dtos.ChallengeInviteDtoRequest;
import com.app.badminton_backend.match.dtos.CreateRoomDtoRequest;
import com.app.badminton_backend.match.dtos.CreateRoomDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.service.ChallengeService;
import com.app.badminton_backend.match.dtos.MyRequestDtoResponse;
import com.app.badminton_backend.match.dtos.MyRoomDtoResponse;
import com.app.badminton_backend.match.dtos.RoomPlayerDto;
import com.app.badminton_backend.match.dtos.MatchHistoryDtoResponse;
import com.app.badminton_backend.match.dtos.AssignTeamsDtoRequest;
import com.app.badminton_backend.auth.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.app.badminton_backend.match.dtos.UpdateRoomDtoRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/challenge-friend")
@AllArgsConstructor
public class ChallengeFriendController {

    private final ChallengeService challengeService;
    private final CurrentUserService currentUserService;

    @PostMapping("/create-room")
    public ResponseEntity<CreateRoomDtoResponse> createChallengeRoom(@Valid @RequestBody CreateRoomDtoRequest createRoomDtoRequest){
        java.time.LocalDateTime scheduledAt = null;
        if (createRoomDtoRequest.getScheduledTime() != null) {
            scheduledAt = java.time.LocalDateTime.parse(createRoomDtoRequest.getScheduledTime(), java.time.format.DateTimeFormatter.ISO_DATE_TIME);
        }
        Match match = challengeService.createChallengeRoom(
            createRoomDtoRequest.getMatchType(),
            createRoomDtoRequest.getMatchName() != null ? createRoomDtoRequest.getMatchName() : "Friendly Match",
            scheduledAt
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateRoomDtoResponse(match.getId()));
    }
    @PostMapping("/invite")
    public ResponseEntity<?> inviteToChallenge(@Valid @RequestBody ChallengeInviteDtoRequest challengeInviteDtoRequest){
        MatchInvite matchInvite = challengeService.inviteByPhone(
                UUID.fromString(challengeInviteDtoRequest.getMatchId()),
                challengeInviteDtoRequest.getPhoneNumber());

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Invite sent successfully");
        response.put("inviteId", matchInvite.getId());
        response.put("status", matchInvite.getStatus());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/accept-invite/{matchId}")
    public ResponseEntity<?> acceptInvite(@PathVariable UUID matchId) {
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        challengeService.joinChallenge(matchId, currentUserId);
        return ResponseEntity.ok(Map.of("message", "Invite accepted"));
    }

    @PostMapping("/decline-invite/{matchId}")
    public ResponseEntity<?> declineInvite(@PathVariable UUID matchId) {
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        challengeService.declineInvite(matchId, currentUserId);
        return ResponseEntity.ok(Map.of("message", "Invite declined"));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<MyRequestDtoResponse>> getMyRequests() {
        return ResponseEntity.ok(challengeService.getMyRequests());
    }

    @GetMapping("/my-rooms")
    public ResponseEntity<List<MyRoomDtoResponse>> getMyRooms() {
        return ResponseEntity.ok(challengeService.getMyRooms());
    }

    @GetMapping("/history")
    public ResponseEntity<List<MatchHistoryDtoResponse>> getMatchHistory() {
        return ResponseEntity.ok(challengeService.getMatchHistory());
    }

    @GetMapping("/{matchId}/players")
    public ResponseEntity<List<RoomPlayerDto>> getRoomPlayers(@PathVariable UUID matchId) {
        return ResponseEntity.ok(challengeService.getRoomPlayers(matchId));
    }

    @PostMapping("/{matchId}/assign-teams")
    public ResponseEntity<?> assignTeams(@PathVariable UUID matchId, @RequestBody AssignTeamsDtoRequest request) {
        challengeService.assignTeams(matchId, request.getTeamAUserIds(), request.getTeamBUserIds());
        return ResponseEntity.ok(Map.of("message", "Teams assigned successfully"));
    }

    @PutMapping("/{matchId}")
    public ResponseEntity<?> updateMatch(@PathVariable UUID matchId, @Valid @RequestBody UpdateRoomDtoRequest request) {
        challengeService.updateMatch(matchId, request);
        return ResponseEntity.ok(Map.of("message", "Match updated successfully"));
    }

    @DeleteMapping("/{matchId}")
    public ResponseEntity<?> deleteMatch(@PathVariable UUID matchId) {
        challengeService.deleteMatch(matchId);
        return ResponseEntity.ok(Map.of("message", "Match deleted successfully"));
    }
}
