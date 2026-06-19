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
import com.app.badminton_backend.match.dtos.MyRequestDtoResponse;
import com.app.badminton_backend.match.dtos.MyRoomDtoResponse;
import com.app.badminton_backend.match.dtos.RoomPlayerDto;
import com.app.badminton_backend.match.dtos.MatchHistoryDtoResponse;
import com.app.badminton_backend.match.dtos.UpdateRoomDtoRequest;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.entity.MatchSet;
import com.app.badminton_backend.match.repository.MatchSetRepository;
import com.app.badminton_backend.profile.repository.ProfileRepository;
import com.app.badminton_backend.profile.entity.Profile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    private final ProfileRepository profileRepository;
    private final MatchSetRepository matchSetRepository;
    //private final CurrentUserService currentUserService;

    /**
     * Step 1: Organizer creates an EMPTY room.
     */
    public Match createChallengeRoom(MatchType matchType, String matchName, java.time.LocalDateTime scheduledAt) {
        UUID organizerId = currentUserService.getCurrentUser().getId();
        int slotsTotal = matchType == MatchType.SINGLES ? 2 : 4;

        Match match = Match.builder()
                .matchType(matchType)
                .origin(MatchOrigin.CHALLENGE)
                .status(MatchStatus.PENDING)
                .organizerId(organizerId)
                .slotsTotal(slotsTotal)
                .slotsJoined(1)
                .matchName(matchName)
                .scheduledAt(scheduledAt)
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
        if (match.getSlotsJoined() >= match.getSlotsTotal()) {
            throw new IllegalStateException("Room is already full");
        }

        User invitedUser = userRepository.findByPhoneNumber(phoneNumber);
        if (invitedUser == null) {
            throw new IllegalArgumentException(
                    "No user found with mobile: " + phoneNumber);
        }

        if (invitedUser.getId().equals(cuurId)) {
            throw new DuplicateException("Self invite is not applicable");
        }

        MatchInvite existingInvite = matchInviteRepository.findByMatchId(matchId)
                .stream()
                .filter(i -> i.getUserId().equals(invitedUser.getId()))
                .findFirst()
                .orElse(null);

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

        if (teamAUserIds.size() != teamBUserIds.size()) {
            throw new IllegalStateException("Team A and Team B must have an equal number of players");
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

    public List<MyRequestDtoResponse> getMyRequests() {
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        List<MatchInvite> invites = matchInviteRepository.findByUserIdAndStatus(currentUserId, InviteStatus.INVITED);
        
        List<MyRequestDtoResponse> requests = new ArrayList<>();
        for (MatchInvite invite : invites) {
            Match match = matchRepository.findById(invite.getMatchId()).orElse(null);
            if (match == null) continue;
            
            Profile organizerProfile = profileRepository.findById(match.getOrganizerId()).orElse(null);
            String organizerName = organizerProfile != null ? organizerProfile.getFirstName() + " " + organizerProfile.getLastName() : "Unknown";
            
            requests.add(MyRequestDtoResponse.builder()
                    .matchId(match.getId())
                    .matchType(match.getMatchType())
                    .organizerName(organizerName)
                    .invitedAt(invite.getInvitedAt())
                    .matchName(match.getMatchName())
                    .scheduledAt(match.getScheduledAt())
                    .build());
        }
        
        requests.sort((a, b) -> {
            if (a.getInvitedAt() == null && b.getInvitedAt() == null) return 0;
            if (a.getInvitedAt() == null) return 1;
            if (b.getInvitedAt() == null) return -1;
            return b.getInvitedAt().compareTo(a.getInvitedAt());
        });
        
        return requests;
    }

    public List<MyRoomDtoResponse> getMyRooms() {
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        List<Match> matches = new ArrayList<>(matchRepository.findByOrganizerId(currentUserId));
        
        List<MatchInvite> joinedInvites = matchInviteRepository.findByUserIdAndStatus(currentUserId, InviteStatus.JOINED);
        for (MatchInvite invite : joinedInvites) {
            Match match = matchRepository.findById(invite.getMatchId()).orElse(null);
            if (match != null && !match.getOrganizerId().equals(currentUserId)) {
                matches.add(match);
            }
        }
        
        List<MyRoomDtoResponse> rooms = new ArrayList<>();
        for (Match match : matches) {
            rooms.add(MyRoomDtoResponse.builder()
                    .matchId(match.getId())
                    .matchType(match.getMatchType())
                    .slotsJoined(match.getSlotsJoined())
                    .slotsTotal(match.getSlotsTotal())
                    .status(match.getStatus())
                    .createdAt(match.getCreatedAt())
                    .matchName(match.getMatchName())
                    .scheduledAt(match.getScheduledAt())
                    .build());
        }
        
        rooms.sort((a, b) -> {
            java.time.LocalDateTime timeA = a.getScheduledAt() != null ? a.getScheduledAt() : a.getCreatedAt();
            java.time.LocalDateTime timeB = b.getScheduledAt() != null ? b.getScheduledAt() : b.getCreatedAt();
            if (timeA == null && timeB == null) return 0;
            if (timeA == null) return 1;
            if (timeB == null) return -1;
            return timeB.compareTo(timeA);
        });

        return rooms;
    }

    public List<RoomPlayerDto> getRoomPlayers(UUID matchId) {
        List<MatchInvite> invites = matchInviteRepository.findByMatchId(matchId);
        List<RoomPlayerDto> players = new ArrayList<>();
        for (MatchInvite invite : invites) {
            User user = userRepository.findById(invite.getUserId()).orElse(null);
            Profile profile = profileRepository.findById(invite.getUserId()).orElse(null);
            if (user != null) {
                players.add(RoomPlayerDto.builder()
                        .userId(user.getId())
                        .name(profile != null ? profile.getFirstName() + " " + profile.getLastName() : "Unknown")
                        .phoneNumber(user.getPhoneNumber())
                        .profilePictureUrl(profile != null ? profile.getProfilePictureUrl() : null)
                        .inviteStatus(invite.getStatus())
                        .isOrganizer(invite.isOrganizer())
                        .build());
            }
        }
        return players;
    }

    public List<MatchHistoryDtoResponse> getMatchHistory() {
        UUID currentUserId = currentUserService.getCurrentUser().getId();
        List<Match> matches = new ArrayList<>(matchRepository.findByOrganizerId(currentUserId));
        
        List<MatchInvite> joinedInvites = matchInviteRepository.findByUserIdAndStatus(currentUserId, InviteStatus.JOINED);
        for (MatchInvite invite : joinedInvites) {
            Match match = matchRepository.findById(invite.getMatchId()).orElse(null);
            if (match != null && !match.getOrganizerId().equals(currentUserId)) {
                matches.add(match);
            }
        }
        
        List<MatchHistoryDtoResponse> history = new ArrayList<>();
        for (Match match : matches) {
            if (match.getStatus() == MatchStatus.COMPLETED || match.getStatus() == MatchStatus.PLAYING) {
                List<MatchSet> sets = matchSetRepository.findByMatchId(match.getId());
                int aWins = 0, bWins = 0;
                for (MatchSet set : sets) {
                    if (set.getSetWinner() == com.app.badminton_backend.match.enums.Team.TEAM_A) aWins++;
                    else if (set.getSetWinner() == com.app.badminton_backend.match.enums.Team.TEAM_B) bWins++;
                }

                // Optional: find user's ELO change if we want, skipping for now to keep it fast
                history.add(MatchHistoryDtoResponse.builder()
                        .matchId(match.getId())
                        .matchName(match.getMatchName())
                        .matchType(match.getMatchType())
                        .status(match.getStatus())
                        .playedAt(match.getPlayedAt())
                        .scheduledAt(match.getScheduledAt())
                        .winnerTeam(match.getWinnerTeam())
                        .teamASetWins(aWins)
                        .teamBSetWins(bWins)
                        .build());
            }
        }
        
        history.sort((a, b) -> {
            if (a.getStatus() == MatchStatus.PLAYING && b.getStatus() != MatchStatus.PLAYING) return -1;
            if (a.getStatus() != MatchStatus.PLAYING && b.getStatus() == MatchStatus.PLAYING) return 1;
            
            java.time.LocalDateTime timeA = a.getPlayedAt() != null ? a.getPlayedAt() : (a.getScheduledAt() != null ? a.getScheduledAt() : java.time.LocalDateTime.MIN);
            java.time.LocalDateTime timeB = b.getPlayedAt() != null ? b.getPlayedAt() : (b.getScheduledAt() != null ? b.getScheduledAt() : java.time.LocalDateTime.MIN);
            return timeB.compareTo(timeA);
        });

        return history;
    }

    @org.springframework.transaction.annotation.Transactional
    public void deleteMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new RuntimeException("Match not found"));
        if (!match.getOrganizerId().equals(currentUserService.getCurrentUser().getId())) {
            throw new RuntimeException("Only the organizer can delete the match");
        }

        matchSetRepository.deleteByMatchId(matchId);
        matchPlayerRepository.deleteByMatchId(matchId);
        matchInviteRepository.deleteByMatchId(matchId);
        matchRepository.delete(match);
    }

    @org.springframework.transaction.annotation.Transactional
    public Match updateMatch(UUID matchId, UpdateRoomDtoRequest request) {
        Match match = matchRepository.findById(matchId).orElseThrow(() -> new RuntimeException("Match not found"));
        if (!match.getOrganizerId().equals(currentUserService.getCurrentUser().getId())) {
            throw new RuntimeException("Only the organizer can update the match");
        }
        
        match.setMatchType(request.getMatchType());
        match.setMatchName(request.getMatchName());
        
        if (request.getScheduledTime() != null) {
            match.setScheduledAt(java.time.LocalDateTime.parse(request.getScheduledTime(), java.time.format.DateTimeFormatter.ISO_DATE_TIME));
        } else {
            match.setScheduledAt(null);
        }
        
        return matchRepository.save(match);
    }
}