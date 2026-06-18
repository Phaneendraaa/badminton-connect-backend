package com.app.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {

    @NotBlank(message = "phone number cannot be empty")
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "invalid phone number format")
    private String phoneNumber;
}
