package com.app.badminton_backend.auth.service;

import com.app.badminton_backend.auth.dto.UserSearchDtoResponse;
import com.app.badminton_backend.auth.entity.User;
import com.app.badminton_backend.auth.repository.UserRepository;
import com.app.badminton_backend.profile.entity.Profile;
import com.app.badminton_backend.profile.service.ProfileService;
import jakarta.persistence.EntityNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@AllArgsConstructor
public class UserService {

    private final ProfileService profileService;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public UserSearchDtoResponse searchUser(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber);
        if (user == null) {
            throw new EntityNotFoundException("User with this Mobile Number doesn't exist");
        }
        UUID userId = user.getId();
        Profile profile = profileService.findByUserId(userId);
        return UserSearchDtoResponse.builder()
                .userId(userId)
                .name(profile.getFirstName() + " " + profile.getLastName())
                .phoneNumber(phoneNumber)
                .profilePictureUrl(profile.getProfilePictureUrl())
                .build();
    }

    /**
     * Stores (or updates) the Expo push token for the currently-authenticated user.
     * Called by the frontend after login or whenever the token rotates.
     *
     * Idempotent: if the same token is saved twice nothing bad happens — the DB
     * row is just updated to the same value.
     */
    @Transactional
    public void savePushToken(String pushToken) {
        User user = currentUserService.getCurrentUser();
        user.setPushToken(pushToken);
        userRepository.save(user);
    }

    /**
     * Clears the push token on logout so the logged-out device stops receiving
     * push notifications for this user.
     */
    @Transactional
    public void clearPushToken() {
        User user = currentUserService.getCurrentUser();
        user.setPushToken(null);
        userRepository.save(user);
    }
}
