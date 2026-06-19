package com.app.badminton_backend.exceptions;

public class PostNotOpenException extends RuntimeException {
    public PostNotOpenException(String message) {
        super(message);
    }
}
