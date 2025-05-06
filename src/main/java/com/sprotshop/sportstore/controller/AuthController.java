package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.LoginRequest;
import com.sprotshop.sportstore.request.RegisterRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for authentication-related endpoints
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * Register a new user with address
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
     * @param loginRequest the login request containing email and password
     * @return ResponseEntity with ApiResponse containing AuthResponse with JWT token and success message
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        ApiResponse<AuthResponse> response = userService.login(loginRequest);
        return ResponseEntity.status(response.getStatus()).body(response);
    }

}
