package com.app.badminton_backend.auth.controller;

import com.app.badminton_backend.auth.dto.CurrentUserDtoResponse;
import com.app.badminton_backend.auth.service.CurrentUserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/current")
@AllArgsConstructor
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    /**
     * Returns the slim user identity DTO.
     * Shape: { userId, phoneNumber, phoneVerified }
     *
     * Does NOT expose role, createdAt, updatedAt or any other internal field.
     * The "userId" key matches the convention used across the rest of the API
     * so the frontend no longer needs the "userData.userId || userData.id" fallback.
     */
    @GetMapping("/user")
    public ResponseEntity<CurrentUserDtoResponse> getCurrentUser() {
        return ResponseEntity.ok(currentUserService.getCurrentUserDto());
    }
}
