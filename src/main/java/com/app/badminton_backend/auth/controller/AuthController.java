package com.app.badminton_backend.auth.controller;

import com.app.badminton_backend.auth.dto.LoginRequestDto;
import com.app.badminton_backend.auth.dto.LoginResponseDto;
import com.app.badminton_backend.auth.dto.PhoneOtpDto;
import com.app.badminton_backend.auth.dto.RefreshTokenRequestDto;
import com.app.badminton_backend.auth.dto.SignUpDtoLocal;
import com.app.badminton_backend.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@AllArgsConstructor
public class AuthController {

    private final AuthService authService;

//    @PostMapping("/signup")
//    public ResponseEntity<String> signupSendOtp(@Valid @RequestBody SignUpDtoLocal signUpDtoLocal) {
//        authService.signupSendOtp(signUpDtoLocal);
//        return ResponseEntity.status(HttpStatus.CREATED).body("OTP sent successfully");
//    }
//
//    @PostMapping("/signup/verify-otp")
//    public ResponseEntity<String> verifySignupOtp(@Valid @RequestBody PhoneOtpDto phoneOtpDto) {
//        String message = authService.verifySignupOtp(phoneOtpDto);
//        return ResponseEntity.ok(message);
//    }

    @PostMapping("/login")
    public ResponseEntity<String> loginSendOtp(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        authService.loginSendOtp(loginRequestDto);
        return ResponseEntity.ok("OTP sent successfully");
    }

    @PostMapping("/login/verify-otp")
    public ResponseEntity<LoginResponseDto> verifyLoginOtp(@Valid @RequestBody PhoneOtpDto phoneOtpDto) {
        LoginResponseDto response = authService.verifyLoginOtp(phoneOtpDto);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<LoginResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        LoginResponseDto response = authService.refreshTokens(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}
