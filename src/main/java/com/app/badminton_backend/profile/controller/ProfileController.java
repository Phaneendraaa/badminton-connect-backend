package com.app.badminton_backend.profile.controller;


import com.app.badminton_backend.profile.dtos.ProfileCreateDto;
import com.app.badminton_backend.profile.service.ProfileService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
