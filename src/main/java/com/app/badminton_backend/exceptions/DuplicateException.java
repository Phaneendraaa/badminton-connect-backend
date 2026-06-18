package com.app.badminton_backend.exceptions;
public class DuplicateException extends RuntimeException{
    public DuplicateException(String msg){
        super(msg);
    }
}