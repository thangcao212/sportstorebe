package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.LoginRequest;
import com.sprotshop.sportstore.request.RegisterRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for authentication-related endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;



    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        ApiResponse<AuthResponse> response = userService.register(registerRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        ApiResponse<AuthResponse> response = userService.login(loginRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    // 👈 VIẾT LẠI: Gửi OTP
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .message("Email không được để trống").status(HttpStatus.BAD_REQUEST.value()).build());
        }
        return ResponseEntity.ok(userService.forgotPassword(email.trim()));
    }

    // 👈 MỚI: Verify OTP + reset (nhận JSON { "email": "...", "otp": "123456", "newPassword": "..." })
    @PostMapping("/verify-otp-reset")
    public ResponseEntity<ApiResponse<String>> verifyOtpAndReset(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String otp = request.get("otp");
        String newPassword = request.get("newPassword");
        try {
            ApiResponse<String> response = userService.verifyOtpAndResetPassword(email, otp, newPassword);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<String>builder()
                    .message(e.getMessage()).status(HttpStatus.BAD_REQUEST.value()).build());
        }
    }


}