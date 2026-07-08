package com.app.badminton_backend.auth.service;

import com.app.badminton_backend.auth.dto.CurrentUserDtoResponse;
import com.app.badminton_backend.auth.entity.CustomUserDetails;
import com.app.badminton_backend.auth.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /**
     * Returns the authenticated User JPA entity.
     * Used internally by other services (e.g. ProfileService, MatchPostService)
     * that need the full entity to derive the user's id or look up related records.
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUser();
    }

    /**
     * Returns a slim DTO suitable for the GET /current/user HTTP response.
     * Exposes only userId, phoneNumber, and phoneVerified — no role,
     * createdAt, updatedAt or other internal fields.
     */
    public CurrentUserDtoResponse getCurrentUserDto() {
        User user = getCurrentUser();
        return CurrentUserDtoResponse.builder()
                .userId(user.getId())
                .phoneNumber(user.getPhoneNumber())
                .phoneVerified(user.isPhoneVerified())
                .build();
    }
}
