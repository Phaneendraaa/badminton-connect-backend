package com.app.badminton_backend.profile.dtos;

import com.app.badminton_backend.profile.enums.GenderEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProfileDtoResponse {
    private UUID id;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private LocalDate dateOfBirth;
    private GenderEnum genderEnum;
    private String homeCity;
}
