package com.app.badminton_backend.elo;

import com.app.badminton_backend.elo.entity.EloPoints;
import com.app.badminton_backend.elo.repository.EloPointsRepository;
import com.app.badminton_backend.elo.service.EloService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EloServiceTest {

    @Mock
    private EloPointsRepository eloPointsRepository;

    private EloService eloService;

    @BeforeEach
    void setUp() {
        eloService = new EloService(eloPointsRepository, new EloCalculator());
        when(eloPointsRepository.save(any(EloPoints.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EloPoints stubPlayer(UUID id, int elo) {
        EloPoints ep = EloPoints.builder().id(id).elo(elo).build();
        when(eloPointsRepository.findById(id)).thenReturn(Optional.of(ep));
        return ep;
    }

    @Test
    void getOrCreate_returnsExistingRow_withoutSaving() {
        UUID id = UUID.randomUUID();
        EloPoints existing = stubPlayer(id, 1234);

        EloPoints result = eloService.getOrCreate(id);

        assertSame(existing, result);
        verify(eloPointsRepository, never()).save(any());
    }

    @Test
    void getOrCreate_createsDefault1000Row_whenMissing() {
        UUID id = UUID.randomUUID();
        when(eloPointsRepository.findById(id)).thenReturn(Optional.empty());

        EloPoints result = eloService.getOrCreate(id);

        ArgumentCaptor<EloPoints> saved = ArgumentCaptor.forClass(EloPoints.class);
        verify(eloPointsRepository).save(saved.capture());
        assertEquals(id, saved.getValue().getId());
        assertEquals(1000, saved.getValue().getElo());
        assertEquals(1000, result.getElo());
    }

    @Test
    void applyMatchResult_singles_equalRatings_movesBothBy16() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        EloPoints winner = stubPlayer(winnerId, 1000);
        EloPoints loser = stubPlayer(loserId, 1000);

        eloService.applyMatchResult(List.of(winnerId), List.of(loserId));

        assertEquals(1016, winner.getElo());
        assertEquals(984, loser.getElo());
    }

    @Test
    void applyMatchResult_singles_upsetGivesBigSwing() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        EloPoints winner = stubPlayer(winnerId, 1000);
        EloPoints loser = stubPlayer(loserId, 1400);

        eloService.applyMatchResult(List.of(winnerId), List.of(loserId));

        assertEquals(1029, winner.getElo());
        assertEquals(1371, loser.getElo());
    }

    @Test
    void applyMatchResult_doubles_usesTeamAverage_andAppliesSameChangeToEveryTeammate() {
        UUID w1 = UUID.randomUUID();
        UUID w2 = UUID.randomUUID();
        UUID l1 = UUID.randomUUID();
        UUID l2 = UUID.randomUUID();
        EloPoints winner1 = stubPlayer(w1, 1000);
        EloPoints winner2 = stubPlayer(w2, 1200); // team average 1100
        EloPoints loser1 = stubPlayer(l1, 1000);
        EloPoints loser2 = stubPlayer(l2, 1000); // team average 1000

        eloService.applyMatchResult(List.of(w1, w2), List.of(l1, l2));

        // expected(1100 vs 1000) = 0.640 -> winners +12, losers -12
        assertEquals(1012, winner1.getElo());
        assertEquals(1212, winner2.getElo());
        assertEquals(988, loser1.getElo());
        assertEquals(988, loser2.getElo());
    }

    @Test
    void applyMatchResult_createsMissingPlayersAt1000_beforeApplyingChange() {
        UUID winnerId = UUID.randomUUID();
        UUID loserId = UUID.randomUUID();
        when(eloPointsRepository.findById(winnerId)).thenReturn(Optional.empty());
        when(eloPointsRepository.findById(loserId)).thenReturn(Optional.empty());

        eloService.applyMatchResult(List.of(winnerId), List.of(loserId));

        ArgumentCaptor<EloPoints> saved = ArgumentCaptor.forClass(EloPoints.class);
        verify(eloPointsRepository, org.mockito.Mockito.atLeast(2)).save(saved.capture());
        List<EloPoints> all = saved.getAllValues();
        // the last save for each player holds the post-match rating
        int winnerElo = all.stream().filter(e -> e.getId().equals(winnerId))
                .reduce((first, second) -> second).orElseThrow().getElo();
        int loserElo = all.stream().filter(e -> e.getId().equals(loserId))
                .reduce((first, second) -> second).orElseThrow().getElo();
        assertEquals(1016, winnerElo);
        assertEquals(984, loserElo);
    }
}
