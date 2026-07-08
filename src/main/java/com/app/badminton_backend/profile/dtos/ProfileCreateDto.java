package com.app.badminton_backend.profile.dtos;

import com.app.badminton_backend.profile.enums.GenderEnum;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@AllArgsConstructor
@lombok.NoArgsConstructor
public class ProfileCreateDto {

    private String firstName;

    private String lastName;

    private String profilePictureUrl;

    private LocalDate dateOfBirth;

    private GenderEnum genderEnum;

    /**
     * Optional: the user's home city, collected at signup for personalised feed defaults.
     * Nullable — existing users who signed up before this field was added won't have it.
     */
    private String homeCity;
}
