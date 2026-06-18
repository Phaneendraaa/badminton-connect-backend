package com.app.badminton_backend.profile.entity;


import com.app.badminton_backend.profile.enums.GenderEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Data
public class Profile {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String profilePictureUrl;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private GenderEnum genderEnum;
}
