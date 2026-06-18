package com.app.badminton_backend.match.controller;


import com.app.badminton_backend.match.dtos.ChallengeInviteDtoRequest;
import com.app.badminton_backend.match.dtos.CreateRoomDtoRequest;
import com.app.badminton_backend.match.dtos.CreateRoomDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.service.ChallengeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/challenge-friend")
@AllArgsConstructor
public class ChallengeFriendController {

    private final ChallengeService challengeService;

    @PostMapping("/create-room")
    public ResponseEntity<CreateRoomDtoResponse> createChallengeRoom(@Valid @RequestBody CreateRoomDtoRequest createRoomDtoRequest){
        Match match = challengeService.createChallengeRoom(createRoomDtoRequest.getMatchType());
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
}
