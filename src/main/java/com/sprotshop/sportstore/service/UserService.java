package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    // Auth
    ApiResponse<AuthResponse> register(RegisterRequest registerRequest);
    ApiResponse<AuthResponse> login(LoginRequest loginRequest);
    User getCurrentLoggedInUser();
    PageResponse<User> getAllUsers(Pageable pageable);
    void deleteUser(Long id);

    // Profile
    ApiResponse<User> getProfile();
    ApiResponse<User> updateProfile(UpdateProfileRequest request);
    ApiResponse<String> uploadAvatar(MultipartFile file);
    ApiResponse<String> changePassword(ChangePasswordRequest request);
//    ApiResponse<String> forgotPassword(ForgotPasswordRequest request);
    ApiResponse<String> logoutAll();

    // Addresses
    ApiResponse<List<Address>> getAddresses();
    ApiResponse<Address> addAddress(AddAddressRequest request);
    ApiResponse<Address> updateAddress(Long id, UpdateAddressRequest request);
    void deleteAddress(Long id);


}