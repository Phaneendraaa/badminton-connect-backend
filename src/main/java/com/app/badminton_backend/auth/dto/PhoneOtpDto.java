package com.app.badminton_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PhoneOtpDto {

    @NotBlank(message = "phone number cannot be empty")
    @Pattern(
            regexp = "^\\+91[6-9]\\d{9}$",
            message = "phone number must be a valid Indian number with +91 prefix"
    )
    private String phoneNumber;

    @NotBlank(message = "otp cannot be empty")
    private String otp;

}
