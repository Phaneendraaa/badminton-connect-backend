package com.app.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SignUpDtoLocal {

    @NotBlank(message = "phone number cannot be empty")
    @Pattern(
            regexp = "^\\+91[6-9]\\d{9}$",
            message = "phone number must be a valid Indian number with +91 prefix"
    )
    private String phoneNumber;
}
