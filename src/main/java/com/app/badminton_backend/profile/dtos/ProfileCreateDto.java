package com.app.badminton_backend.profile.dtos;

import com.app.badminton_backend.profile.enums.GenderEnum;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ProfileCreateDto {

    private String firstName;

    private String lastName;

    private String profilePictureUrl;

    private LocalDate dateOfBirth;

    private GenderEnum genderEnum;
}
