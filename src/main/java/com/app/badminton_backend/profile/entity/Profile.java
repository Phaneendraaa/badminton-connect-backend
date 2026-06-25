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

    /**
     * The user's preferred home city for match discovery.
     * Used to default the city filter on the Home feed so users don't have to
     * re-select it every session. Editable from the Profile screen.
     * Nullable — not required during initial profile creation.
     */
    @Column(length = 100)
    private String homeCity;
}
