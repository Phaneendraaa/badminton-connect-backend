package com.app.badminton_backend.match.entity;


import com.app.badminton_backend.match.enums.Team;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchPlayer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Team team; // TEAM_A or TEAM_B (singles just uses one player per team)

    @Column(nullable = false)
    private Integer eloBefore;

    private Integer eloAfter;

    private Integer eloChange;
}
