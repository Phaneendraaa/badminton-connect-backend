package com.app.badminton_backend.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSearchDtoRequest {
    public String phoneNumber;
}
