package com.app.badminton_backend.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class UserSearchDtoResponse {

    private UUID userId;
    private String name;
    private String phoneNumber;
    private String profilePictureUrl;
}
