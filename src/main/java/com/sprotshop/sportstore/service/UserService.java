package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.request.LoginRequest;
import com.sprotshop.sportstore.request.RegisterRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Pageable;

public interface UserService {
    /**
     * Register a new user with address
     * @param registerRequest the registration request containing user and address information
     * @return ApiResponse containing AuthResponse with JWT token and success message
     */
    ApiResponse<AuthResponse> register(RegisterRequest registerRequest);

    /**
     * Authenticate a user and generate JWT token
     * @param loginRequest the login request containing email and password
     * @return ApiResponse containing AuthResponse with JWT token and success message
     */
    ApiResponse<AuthResponse> login(LoginRequest loginRequest);
    User getCurrentLoggedInUser();
    PageResponse<User> getAllUsers(Pageable pageable);

    void deleteUser(Long id);



}
