package com.app.badminton_backend.profile.controller;


import com.app.badminton_backend.profile.dtos.ProfileCreateDto;
import com.app.badminton_backend.profile.dtos.ProfileDtoResponse;
import com.app.badminton_backend.profile.dtos.ProfileUpdateDto;
import com.app.badminton_backend.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/profile")
@AllArgsConstructor
public class ProfileController {

    private ProfileService profileService;

    @PostMapping("/create")
    public ResponseEntity<?> createProfile(@Valid @RequestBody ProfileCreateDto profileCreateDto){
        profileService.createProfile(profileCreateDto);
        return ResponseEntity.ok("Profile created");
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileDtoResponse> getMyProfile() {
        return ResponseEntity.ok(profileService.getMyProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<ProfileDtoResponse> updateMyProfile(@Valid @RequestBody ProfileUpdateDto updateDto) {
        return ResponseEntity.ok(profileService.updateMyProfile(updateDto));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileDtoResponse> getProfileById(@PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getProfileById(userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse> getStats() {
        return ResponseEntity.ok(profileService.getStats());
    }
}
