package com.app.badminton_backend.profile.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProfileUpdateDto {
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String homeCity;
}
