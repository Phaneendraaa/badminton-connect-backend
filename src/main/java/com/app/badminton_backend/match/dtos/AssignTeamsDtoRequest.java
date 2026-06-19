package com.app.badminton_backend.match.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssignTeamsDtoRequest {
    private List<UUID> teamAUserIds;
    private List<UUID> teamBUserIds;
}
