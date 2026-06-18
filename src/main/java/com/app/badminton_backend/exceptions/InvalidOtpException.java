package com.app.badminton_backend.exceptions;

public class InvalidOtpException extends RuntimeException{
    public InvalidOtpException(String msg){
        super(msg);
    }
}
