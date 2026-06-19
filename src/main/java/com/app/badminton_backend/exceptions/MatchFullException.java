package com.app.badminton_backend.exceptions;

public class MatchFullException extends RuntimeException {
    public MatchFullException(String message) {
        super(message);
    }
}
