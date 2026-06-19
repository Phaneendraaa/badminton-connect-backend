package com.app.badminton_backend.match.enums;

public enum JoinRequestStatus {
    PENDING,   // awaiting organizer decision
    ACCEPTED,  // organizer accepted — player is now a MatchPlayer
    REJECTED,  // organizer rejected
    CANCELLED  // requester cancelled their own request
}
