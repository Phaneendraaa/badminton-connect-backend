package com.app.badminton_backend.match;

import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.service.CurrentUserService;
import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.match.dtos.UnifiedRoomDtoResponse;
import com.app.badminton_backend.match.entity.Match;
import com.app.badminton_backend.match.entity.MatchInvite;
import com.app.badminton_backend.match.entity.MatchPlayer;
import com.app.badminton_backend.match.enums.InviteStatus;
import com.app.badminton_backend.match.enums.MatchOrigin;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.Team;
import com.app.badminton_backend.match.repository.MatchInviteRepository;
import com.app.badminton_backend.match.repository.MatchPlayerRepository;
import com.app.badminton_backend.match.repository.MatchRepository;
import com.app.badminton_backend.match.service.MatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceTest {

    @Mock private CurrentUserService currentUserService;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchPlayerRepository matchPlayerRepository;
    @Mock private MatchInviteRepository matchInviteRepository;
    @Mock private EloPointsRepository eloPointsRepository;

    private MatchService matchService;
    private UUID me;

    @BeforeEach
    void setUp() {
        matchService = new MatchService(currentUserService, matchRepository, matchPlayerRepository,
                matchInviteRepository, eloPointsRepository);
        me = UUID.randomUUID();
        when(currentUserService.getCurrentUser()).thenReturn(User.builder().id(me).build());
        when(matchRepository.findByOrganizerId(any())).thenReturn(List.of());
        when(matchInviteRepository.findByUserIdAndStatus(any(), any())).thenReturn(List.of());
        when(matchPlayerRepository.findByUserId(any())).thenReturn(List.of());
    }

    private Match match(UUID id, UUID organizer, MatchStatus status, LocalDateTime scheduledAt) {
        Match m = Match.builder()
                .id(id)
                .matchType(MatchType.DOUBLES)
                .origin(MatchOrigin.OPEN)
                .matchName("match-" + id.toString().substring(0, 4))
                .organizerId(organizer)
                .status(status)
                .scheduledAt(scheduledAt)
                .slotsTotal(4)
                .build();
        when(matchRepository.findById(id)).thenReturn(Optional.of(m));
        return m;
    }

    // ── createMatch ───────────────────────────────────────────────────────────

    @Test
    void createMatch_singles_rejectsTeamsThatAreNotOneVsOne() {
        List<UUID> twoPlayers = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<UUID> onePlayer = List.of(UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> matchService.createMatch(MatchType.SINGLES, MatchOrigin.CHALLENGE, null,
                        me, twoPlayers, onePlayer));

        assertTrue(ex.getMessage().contains("SINGLES"));
        verify(matchRepository, never()).save(any());
    }

    @Test
    void createMatch_doubles_rejectsTeamsThatAreNotTwoVsTwo() {
        List<UUID> onePlayer = List.of(UUID.randomUUID());
        List<UUID> twoPlayers = List.of(UUID.randomUUID(), UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> matchService.createMatch(MatchType.DOUBLES, MatchOrigin.OPEN, UUID.randomUUID(),
                        me, onePlayer, twoPlayers));
        verify(matchRepository, never()).save(any());
    }

    @Test
    void createMatch_doubles_savesPendingMatch_andSnapshotsEachPlayersEloAndTeam() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID(), b1 = UUID.randomUUID(), b2 = UUID.randomUUID();
        UUID savedMatchId = UUID.randomUUID();
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(savedMatchId);
            return m;
        });
        int[] elos = {1000, 1100, 1200, 1300};
        UUID[] ids = {a1, a2, b1, b2};
        for (int i = 0; i < ids.length; i++) {
            when(eloPointsRepository.findById(ids[i]))
                    .thenReturn(Optional.of(EloPoints.builder().id(ids[i]).elo(elos[i]).build()));
        }

        Match created = matchService.createMatch(MatchType.DOUBLES, MatchOrigin.OPEN, null,
                me, List.of(a1, a2), List.of(b1, b2));

        assertEquals(MatchStatus.PENDING, created.getStatus());
        assertEquals(savedMatchId, created.getId());

        ArgumentCaptor<MatchPlayer> players = ArgumentCaptor.forClass(MatchPlayer.class);
        verify(matchPlayerRepository, times(4)).save(players.capture());
        List<MatchPlayer> saved = players.getAllValues();
        assertEquals(List.of(a1, a2, b1, b2), saved.stream().map(MatchPlayer::getUserId).toList());
        assertEquals(List.of(Team.TEAM_A, Team.TEAM_A, Team.TEAM_B, Team.TEAM_B),
                saved.stream().map(MatchPlayer::getTeam).toList());
        assertEquals(List.of(1000, 1100, 1200, 1300),
                saved.stream().map(MatchPlayer::getEloBefore).toList());
        assertTrue(saved.stream().allMatch(p -> savedMatchId.equals(p.getMatchId())));
    }

    @Test
    void createMatch_failsFast_whenAPlayerHasNoEloRow() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(eloPointsRepository.findById(a)).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> matchService.createMatch(MatchType.SINGLES, MatchOrigin.CHALLENGE, null,
                        me, List.of(a), List.of(b)));

        assertTrue(ex.getMessage().contains(a.toString()));
    }

    // ── getUnifiedMyRooms ─────────────────────────────────────────────────────

    @Test
    void myRooms_isEmpty_whenUserHasNoMatches() {
        assertTrue(matchService.getUnifiedMyRooms().isEmpty());
    }

    @Test
    void myRooms_includesOrganizedMatches_butExcludesCompletedOnes() {
        Match open = match(UUID.randomUUID(), me, MatchStatus.PENDING, LocalDateTime.now().plusDays(1));
        Match done = match(UUID.randomUUID(), me, MatchStatus.COMPLETED, LocalDateTime.now().minusDays(1));
        when(matchRepository.findByOrganizerId(me)).thenReturn(List.of(open, done));

        List<UnifiedRoomDtoResponse> rooms = matchService.getUnifiedMyRooms();

        assertEquals(1, rooms.size());
        assertEquals(open.getId(), rooms.get(0).getMatchId());
        assertEquals(MatchStatus.PENDING, rooms.get(0).getStatus());
    }

    @Test
    void myRooms_includesChallengeMatchesWhereInviteIsJoined() {
        UUID organizer = UUID.randomUUID();
        Match challenge = match(UUID.randomUUID(), organizer, MatchStatus.PENDING, LocalDateTime.now().plusHours(5));
        when(matchInviteRepository.findByUserIdAndStatus(me, InviteStatus.JOINED))
                .thenReturn(List.of(MatchInvite.builder().matchId(challenge.getId()).userId(me)
                        .status(InviteStatus.JOINED).build()));

        List<UnifiedRoomDtoResponse> rooms = matchService.getUnifiedMyRooms();

        assertEquals(List.of(challenge.getId()), rooms.stream().map(UnifiedRoomDtoResponse::getMatchId).toList());
    }

    @Test
    void myRooms_includesOpenMatchesWhereIAmAPlayer() {
        UUID organizer = UUID.randomUUID();
        Match joined = match(UUID.randomUUID(), organizer, MatchStatus.PLAYING, LocalDateTime.now().plusHours(1));
        when(matchPlayerRepository.findByUserId(me))
                .thenReturn(List.of(MatchPlayer.builder().matchId(joined.getId()).userId(me)
                        .team(Team.UNASSIGNED).eloBefore(1000).build()));

        List<UnifiedRoomDtoResponse> rooms = matchService.getUnifiedMyRooms();

        assertEquals(1, rooms.size());
        assertEquals(joined.getId(), rooms.get(0).getMatchId());
    }

    @Test
    void myRooms_deduplicatesAMatchFoundThroughSeveralSources() {
        UUID organizer = UUID.randomUUID();
        Match shared = match(UUID.randomUUID(), organizer, MatchStatus.PENDING, LocalDateTime.now().plusHours(2));
        when(matchInviteRepository.findByUserIdAndStatus(me, InviteStatus.JOINED))
                .thenReturn(List.of(MatchInvite.builder().matchId(shared.getId()).userId(me)
                        .status(InviteStatus.JOINED).build()));
        when(matchPlayerRepository.findByUserId(me))
                .thenReturn(List.of(MatchPlayer.builder().matchId(shared.getId()).userId(me)
                        .team(Team.TEAM_A).eloBefore(1000).build()));

        assertEquals(1, matchService.getUnifiedMyRooms().size());
    }

    @Test
    void myRooms_doesNotListOtherPeoplesCompletedMatches() {
        UUID organizer = UUID.randomUUID();
        Match finished = match(UUID.randomUUID(), organizer, MatchStatus.COMPLETED, LocalDateTime.now().minusDays(2));
        when(matchPlayerRepository.findByUserId(me))
                .thenReturn(List.of(MatchPlayer.builder().matchId(finished.getId()).userId(me)
                        .team(Team.TEAM_B).eloBefore(1000).build()));

        assertTrue(matchService.getUnifiedMyRooms().isEmpty());
    }

    @Test
    void myRooms_sortsSoonestFirst_andFallsBackToCreatedAtWhenNotScheduled() {
        Match later = match(UUID.randomUUID(), me, MatchStatus.PENDING, LocalDateTime.now().plusDays(3));
        Match sooner = match(UUID.randomUUID(), me, MatchStatus.PENDING, LocalDateTime.now().plusHours(2));
        Match unscheduled = match(UUID.randomUUID(), me, MatchStatus.PENDING, null);
        unscheduled.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(matchRepository.findByOrganizerId(me)).thenReturn(List.of(later, sooner, unscheduled));

        List<UUID> order = matchService.getUnifiedMyRooms().stream()
                .map(UnifiedRoomDtoResponse::getMatchId).toList();

        // unscheduled falls back to createdAt (yesterday), so it comes first
        assertEquals(List.of(unscheduled.getId(), sooner.getId(), later.getId()), order);
    }

    @Test
    void myRooms_mapsMatchFieldsOntoTheDto() {
        LocalDateTime scheduled = LocalDateTime.now().plusDays(1).withNano(0);
        Match m = match(UUID.randomUUID(), me, MatchStatus.PENDING, scheduled);
        m.setSlotsJoined(3);
        UUID postId = UUID.randomUUID();
        m.setPostId(postId);
        when(matchRepository.findByOrganizerId(me)).thenReturn(List.of(m));

        UnifiedRoomDtoResponse dto = matchService.getUnifiedMyRooms().get(0);

        assertEquals(m.getMatchName(), dto.getMatchName());
        assertEquals(MatchType.DOUBLES, dto.getMatchType());
        assertEquals(MatchOrigin.OPEN, dto.getOrigin());
        assertEquals(scheduled, dto.getScheduledAt());
        assertEquals(3, dto.getSlotsJoined());
        assertEquals(4, dto.getSlotsTotal());
        assertEquals(postId, dto.getPostId());
    }
}
