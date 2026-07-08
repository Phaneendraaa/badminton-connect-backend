package com.app.badminton_backend.auth.controller;

import com.app.badminton_backend.auth.dto.UserSearchDtoRequest;
import com.app.badminton_backend.auth.dto.UserSearchDtoResponse;
import com.app.badminton_backend.auth.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/search")
    public ResponseEntity<?> searchUser(@Valid @RequestBody UserSearchDtoRequest userSearchDtoRequest) {
        UserSearchDtoResponse userSearchDtoResponse = userService.searchUser(userSearchDtoRequest.getPhoneNumber());
        return ResponseEntity.status(HttpStatus.OK).body(userSearchDtoResponse);
    }

    /**
     * Registers (or updates) the Expo push token for the current user's device.
     *
     * Called by the frontend after login and whenever the token rotates.
     * Idempotent — safe to call multiple times with the same token.
     *
     * Body: { "pushToken": "ExponentPushToken[xxxx]" }
     */
    @PostMapping("/push-token")
    public ResponseEntity<?> savePushToken(@RequestBody Map<String, String> body) {
        String pushToken = body.get("pushToken");
        if (pushToken == null || pushToken.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "pushToken is required"));
        }
        userService.savePushToken(pushToken);
        return ResponseEntity.ok(Map.of("message", "Push token registered"));
    }

    /**
     * Clears the push token on logout so the logged-out device stops receiving
     * notifications for this user.
     */
    @DeleteMapping("/push-token")
    public ResponseEntity<?> clearPushToken() {
        userService.clearPushToken();
        return ResponseEntity.ok(Map.of("message", "Push token cleared"));
    }
}
