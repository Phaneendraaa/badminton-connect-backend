package com.app.badminton_backend.profile.controller;


import com.app.badminton_backend.profile.dtos.ProfileCreateDto;
import com.app.badminton_backend.profile.dtos.ProfileDtoResponse;
import com.app.badminton_backend.profile.dtos.ProfileUpdateDto;
import com.app.badminton_backend.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * Public stats for any user profile.
     * Returns matchesPlayed, winRate, trustScore for the given userId.
     */
    @GetMapping("/{userId}/stats")
    public ResponseEntity<com.app.badminton_backend.profile.dtos.ProfileStatsDtoResponse> getPublicStats(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(profileService.getPublicStats(userId));
    }

    /**
     * Case-insensitive partial search across player names.
     * GET /profile/search?q=john  → list of matching profiles (max 50).
     */
    @GetMapping("/search")
    public ResponseEntity<List<ProfileDtoResponse>> searchProfiles(
            @RequestParam(name = "q", required = false, defaultValue = "") String query) {
        return ResponseEntity.ok(profileService.searchProfiles(query));
    }
}

