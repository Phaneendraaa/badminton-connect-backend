package com.app.badminton_backend.match.service;

import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.repository.UserRepository;
import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.service.EloService;
import com.app.badminton_backend.exceptions.DuplicateException;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.*;
import com.app.badminton_backend.match.repository.MatchInviteRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChallengeService {

    private final CurrentUserService currentUserService;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final UserRepository userRepository;
    private final MatchInviteRepository matchInviteRepository;
    private final EloService eloService;
    //private final CurrentUserService currentUserService;

    /**
     * Step 1: Organizer creates an EMPTY room.
     */
    public Match createChallengeRoom(MatchType matchType) {
        UUID organizerId = currentUserService.getCurrentUser().getId();
        int slotsTotal = matchType == MatchType.SINGLES ? 2 : 4;

        Match match = Match.builder()
                .matchType(matchType)
                .origin(MatchOrigin.CHALLENGE)
                .status(MatchStatus.PENDING)
                .organizerId(organizerId)
                .slotsTotal(slotsTotal)
                .slotsJoined(1)
                .build();

        match = matchRepository.save(match);

        MatchInvite organizerInvite = MatchInvite.builder()
                .matchId(match.getId())
                .userId(organizerId)
                .isOrganizer(true)
                .status(InviteStatus.JOINED)
                .build();

        matchInviteRepository.save(organizerInvite);

        return match;
    }

    /**
     * Step 2: Organizer invites players.
     */
    public MatchInvite inviteByPhone(UUID matchId, String phoneNumber) {
        UUID cuurId = currentUserService.getCurrentUser().getId();
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("Match not found"));
        System.out.println("match found");
        if (match.getSlotsJoined() >= match.getSlotsTotal()) {
            throw new IllegalStateException("Room is already full");
        }

        User invitedUser = userRepository.findByPhoneNumber(phoneNumber);
        System.out.println("user->"+invitedUser.getId());
        if (invitedUser == null) {
            throw new IllegalArgumentException(
                    "No user found with mobile: " + phoneNumber);
        }

        MatchInvite existingInvite = matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getUserId().equals(invitedUser.getId()))
                .findFirst()
                .orElse(null);
        if(existingInvite.getUserId()==cuurId){
            throw new DuplicateException("Self invite is not applicable");
        }
        if (existingInvite != null) {

            if (existingInvite.getStatus() == InviteStatus.INVITED) {
                throw new IllegalStateException("Invite already pending");
            }

            if (existingInvite.getStatus() == InviteStatus.JOINED) {
                throw new IllegalStateException("User already joined");
            }

            if (existingInvite.getStatus() == InviteStatus.DECLINED
                    || existingInvite.getStatus() == InviteStatus.REMOVED) {

                existingInvite.setStatus(InviteStatus.INVITED);
                return matchInviteRepository.save(existingInvite);
            }
        }

        return matchInviteRepository.save(
                MatchInvite.builder()
                        .matchId(matchId)
                        .userId(invitedUser.getId())
                        .isOrganizer(false)
                        .status(InviteStatus.INVITED)
                        .build()
        );
    }

    /**
     * Step 3: Invited player accepts and joins.
     */
    public void joinChallenge(UUID matchId, UUID userId) {

        MatchInvite invite = matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("User not invited to this match"));

        if (invite.getStatus() != InviteStatus.INVITED) {
            throw new IllegalStateException("Invalid invite status");
        }

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("Match not found"));

        if (match.getSlotsJoined() >= match.getSlotsTotal()) {
            throw new IllegalStateException("Room is already full");
        }

        invite.setStatus(InviteStatus.JOINED);
        matchInviteRepository.save(invite);

        match.setSlotsJoined(match.getSlotsJoined() + 1);
        matchRepository.save(match);
    }

    /**
     * Invited user declines invitation.
     */
    public void declineInvite(UUID matchId, UUID userId) {

        MatchInvite invite = matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Invite not found"));

        if (invite.getStatus() != InviteStatus.INVITED) {
            throw new IllegalStateException("Invite cannot be declined");
        }

        invite.setStatus(InviteStatus.DECLINED);
        matchInviteRepository.save(invite);
    }

    /**
     * Joined player leaves room before teams are assigned.
     */
    public void leaveRoom(UUID matchId, UUID userId) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("Match not found"));

        if (match.getStatus() != MatchStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot leave after teams are assigned");
        }

        MatchInvite invite = matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Invite not found"));

        if (invite.isOrganizer()) {
            throw new IllegalStateException("Organizer cannot leave the room");
        }

        if (invite.getStatus() != InviteStatus.JOINED) {
            throw new IllegalStateException("User has not joined");
        }

        invite.setStatus(InviteStatus.REMOVED);
        matchInviteRepository.save(invite);

        match.setSlotsJoined(match.getSlotsJoined() - 1);
        matchRepository.save(match);
    }

    /**
     * Get all joined players.
     */
    public List<MatchInvite> getJoinedPlayers(UUID matchId) {

        return matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getStatus() == InviteStatus.JOINED)
                .toList();
    }

    /**
     * Organizer assigns teams once room is full.
     */
    public void assignTeams(UUID matchId,
                            List<UUID> teamAUserIds,
                            List<UUID> teamBUserIds) {

        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalStateException("Match not found"));

        if (!match.getSlotsJoined().equals(match.getSlotsTotal())) {
            throw new IllegalStateException("Not all players have joined yet");
        }

        int totalPlayers = teamAUserIds.size() + teamBUserIds.size();

        if (totalPlayers != match.getSlotsTotal()) {
            throw new IllegalStateException(
                    "Assigned players count does not match match size");
        }

        for (UUID userId : teamAUserIds) {
            if (teamBUserIds.contains(userId)) {
                throw new IllegalStateException(
                        "A player cannot belong to both teams");
            }
        }

        for (UUID userId : teamAUserIds) {
            addMatchPlayer(matchId, userId, Team.TEAM_A);
        }

        for (UUID userId : teamBUserIds) {
            addMatchPlayer(matchId, userId, Team.TEAM_B);
        }

        match.setStatus(MatchStatus.CREATED);
        matchRepository.save(match);
    }

    private void addMatchPlayer(UUID matchId, UUID userId, Team team) {

        int eloBefore = eloService.getOrCreate(userId).getElo();

        matchPlayerRepository.save(
                MatchPlayer.builder()
                        .matchId(matchId)
                        .userId(userId)
                        .team(team)
                        .eloBefore(eloBefore)
                        .build()
        );
    }
}