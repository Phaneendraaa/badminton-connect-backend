package com.app.badminton_backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Slim projection of the authenticated user returned by GET /current/user.
 *
 * Deliberately exposes only the three fields the frontend needs:
 *   - userId       (matches the "userId" key used across the rest of the API)
 *   - phoneNumber  (displayed in profile / used as identity)
 *   - phoneVerified (gate for features that require a verified number)
 *
 * Internal fields (role, createdAt, updatedAt, isPhoneVerified raw boolean)
 * are intentionally omitted to avoid leaking implementation details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserDtoResponse {

    /** The user's primary key — named "userId" for consistency with the rest of the API. */
    private UUID userId;

    private String phoneNumber;

    private boolean phoneVerified;
}
