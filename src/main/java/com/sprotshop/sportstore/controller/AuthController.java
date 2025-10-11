package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.LoginRequest;
import com.sprotshop.sportstore.request.RegisterRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.service.GoogleAuthService;
import com.sprotshop.sportstore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

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
    private final GoogleAuthService googleAuthService;

    /**
     * Register a new user with address
     *
     * @param registerRequest the registration request containing user and address information
     * @return ResponseEntity with ApiResponse containing AuthResponse with JWT token and success message
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        ApiResponse<AuthResponse> response = userService.register(registerRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Authenticate a user and generate JWT token
     *
     * @param loginRequest the login request containing email and password
     * @return ResponseEntity with ApiResponse containing AuthResponse with JWT token and success message
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        ApiResponse<AuthResponse> response = userService.login(loginRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

    /**
     * Google login endpoint (POST with JSON body for security)
     * Frontend gửi: { "accessToken": "..." }
     */
//    @GetMapping("/google")
//    public ResponseEntity<Void> googleLogin() {
//        // Frontend sẽ gọi /oauth2/authorization/google trực tiếp, nhưng nếu cần redirect từ API
//        return ResponseEntity.ok().build();  // Hoặc redirect thủ công
//    }
}