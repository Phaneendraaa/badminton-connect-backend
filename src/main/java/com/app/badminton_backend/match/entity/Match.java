package com.app.badminton_backend.match.entity;


import com.app.badminton_backend.match.enums.MatchOrigin;
import com.app.badminton_backend.match.enums.MatchStatus;
import com.app.badminton_backend.match.enums.MatchType;
import com.app.badminton_backend.match.enums.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "badminton_match")
public class Match {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType matchType; // SINGLES, DOUBLES

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchOrigin origin; // POST (anonymous/open) or CHALLENGE (friend direct)

    @Column(nullable = false)
    private String matchName;

    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MatchStatus status = MatchStatus.PENDING; //

    private UUID postId; // nullable — only set if origin = POST

    @Column(nullable = false)
    private UUID organizerId; // who has final say on result — post creator OR challenger

    @Enumerated(EnumType.STRING)
    private Team winnerTeam; // TEAM_A, TEAM_B — null until confirmed

    /** Organizer-chosen display name for Team A. Defaults to "Team A" if not set. */
    @Builder.Default
    private String teamAName = "Team A";

    /** Organizer-chosen display name for Team B. Defaults to "Team B" if not set. */
    @Builder.Default
    private String teamBName = "Team B";

    @Column(nullable = false)
    private Integer slotsTotal; // 2 for singles, 4 for doubles

    @Builder.Default
    private Integer slotsJoined = 1; // organizer auto-counts as joined

    private LocalDateTime playedAt;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Optimistic-lock version. JPA auto-increments this on every UPDATE.
     * The accept-request path uses a PESSIMISTIC_WRITE lock instead for
     * slot contention, but this field guards any other concurrent writes
     * (e.g. two simultaneous updateMatch calls).
     */
    @Version
    private Integer version;
}