package com.app.badminton_backend.match.enums;

public enum NotificationType {
    JOIN_REQUEST_RECEIVED,   // organizer: someone requested to join your post
    JOIN_REQUEST_ACCEPTED,   // requester: your join request was accepted
    JOIN_REQUEST_REJECTED,   // requester: your join request was rejected
    POST_FULL,               // all players: the match is now full
    POST_CANCELLED,          // all players: the post/match was cancelled
    MATCH_STARTING_SOON,     // all players: match starts in ~1 hour
    NEW_CHAT_MESSAGE         // participant: someone sent a chat message
}
