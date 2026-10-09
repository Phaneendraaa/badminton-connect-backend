package com.app.badminton_backend.profile;

import com.app.badminton_backend.profile.entity.PlayerTrustScore;
import com.app.badminton_backend.profile.repository.PlayerTrustScoreRepository;
import com.app.badminton_backend.profile.service.PlayerTrustScoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerTrustScoreServiceTest {

    @Mock
    private PlayerTrustScoreRepository repository;

    private PlayerTrustScoreService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new PlayerTrustScoreService(repository);
        userId = UUID.randomUUID();
        when(repository.save(any(PlayerTrustScore.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PlayerTrustScore existing(int score, int matchesPlayed, int noShows) {
        PlayerTrustScore pts = PlayerTrustScore.builder()
                .userId(userId)
                .score(score)
                .matchesPlayed(matchesPlayed)
                .noShowCount(noShows)
                .build();
        when(repository.findById(userId)).thenReturn(Optional.of(pts));
        return pts;
    }

    @Test
    void getOrCreate_returnsExistingRow_withoutSaving() {
        PlayerTrustScore pts = existing(55, 3, 1);

        assertSame(pts, service.getOrCreate(userId));
        verify(repository, never()).save(any());
    }

    @Test
    void getOrCreate_createsRowWithDefaultScore80_whenMissing() {
        when(repository.findById(userId)).thenReturn(Optional.empty());

        PlayerTrustScore result = service.getOrCreate(userId);

        ArgumentCaptor<PlayerTrustScore> saved = ArgumentCaptor.forClass(PlayerTrustScore.class);
        verify(repository).save(saved.capture());
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(80, result.getScore());
        assertEquals(0, result.getMatchesPlayed());
        assertEquals(0, result.getNoShowCount());
        assertEquals(0, result.getReportCount());
    }

    @Test
    void applyDelta_addsPositiveDelta() {
        existing(80, 0, 0);
        assertEquals(85, service.applyDelta(userId, 5).getScore());
    }

    @Test
    void applyDelta_subtractsNegativeDelta() {
        existing(80, 0, 0);
        assertEquals(60, service.applyDelta(userId, -20).getScore());
    }

    @Test
    void applyDelta_clampsAt100() {
        existing(95, 0, 0);
        assertEquals(100, service.applyDelta(userId, 50).getScore());
    }

    @Test
    void applyDelta_clampsAtZero() {
        existing(5, 0, 0);
        assertEquals(0, service.applyDelta(userId, -50).getScore());
    }

    @Test
    void applyDelta_refreshesUpdatedAtTimestamp() {
        PlayerTrustScore pts = existing(80, 0, 0);
        java.time.LocalDateTime before = java.time.LocalDateTime.now().minusDays(1);
        pts.setUpdatedAt(before);

        PlayerTrustScore result = service.applyDelta(userId, 1);

        assertNotNull(result.getUpdatedAt());
        assertEquals(true, result.getUpdatedAt().isAfter(before));
    }

    @Test
    void recordNoShow_deducts10Points_andIncrementsNoShowCount() {
        existing(80, 4, 0);

        PlayerTrustScore result = service.recordNoShow(userId);

        assertEquals(70, result.getScore());
        assertEquals(1, result.getNoShowCount());
        assertEquals(4, result.getMatchesPlayed(), "a no-show must not change matchesPlayed");
    }

    @Test
    void recordNoShow_neverDropsBelowZero() {
        existing(4, 0, 7);

        PlayerTrustScore result = service.recordNoShow(userId);

        assertEquals(0, result.getScore());
        assertEquals(8, result.getNoShowCount());
    }

    @Test
    void recordMatchCompleted_awards1Point_andIncrementsMatchesPlayed() {
        existing(80, 2, 0);

        PlayerTrustScore result = service.recordMatchCompleted(userId);

        assertEquals(81, result.getScore());
        assertEquals(3, result.getMatchesPlayed());
    }

    @Test
    void recordMatchCompleted_capsScoreAt100() {
        existing(100, 10, 0);

        PlayerTrustScore result = service.recordMatchCompleted(userId);

        assertEquals(100, result.getScore());
        assertEquals(11, result.getMatchesPlayed());
    }
}
