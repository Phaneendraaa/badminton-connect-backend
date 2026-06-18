package com.app.badminton_backend.elo.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EloPoints {

    @Id
    private UUID id;

    @Column(nullable = false)
    @Builder.Default
    private Integer elo=1000;


}
