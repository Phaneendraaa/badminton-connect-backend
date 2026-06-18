package com.app.badminton_backend.match.entity;
import com.app.badminton_backend.match.enums.Team;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchSet {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private Integer setNumber; // 1, 2, or 3

    @Column(nullable = false)
    private Integer teamAScore; // e.g. 21

    @Column(nullable = false)
    private Integer teamBScore; // e.g. 18

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Team setWinner; // TEAM_A or TEAM_B for this specific set
}
