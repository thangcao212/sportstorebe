package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.User;

import com.sprotshop.sportstore.entity.Message;
import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.MessagingService;
import com.sprotshop.sportstore.service.UserService;
import com.sprotshop.sportstore.service.UserStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserStatsService userStatsService;
    private final MessagingService messagingService; // 👈 Thêm ChatService

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<User>>> getAllUsers(Pageable pageable) {
        PageResponse<User> users = userService.getAllUsers(pageable);
        ApiResponse<PageResponse<User>> response = ApiResponse.<PageResponse<User>>builder()
                .status(HttpStatus.OK.value())
                .message("Danh sách người dùng !")
                .data(users)
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        ApiResponse<String> response = ApiResponse.<String>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa người dùng thành công !")
                .build();
        return ResponseEntity.ok(response);
    }


    // 👈 Admin endpoints


    // 👈 Các endpoints cũ của User (profile, address, etc.) - giữ nguyên, đã dùng ApiResponse
    @GetMapping("/daily")
    public ApiResponse<Map<Integer, Long>> getDaily(
            @RequestParam int month,
            @RequestParam int year
    ) {
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Thống kê user mới theo ngày")
                .data(userStatsService.getNewUsersByDay(month, year))
                .build();
    }

    @GetMapping("/monthly")
    public ApiResponse<Map<Integer, Long>> getMonthly(@RequestParam int year) {
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Thống kê user mới theo tháng")
                .data(userStatsService.getNewUsersByMonth(year))
                .build();
    }

    @GetMapping("/yearly")
    public ApiResponse<Map<Integer, Long>> getYearly() {
        return ApiResponse.<Map<Integer, Long>>builder()
                .status(200)
                .message("Thống kê user mới theo năm")
                .data(userStatsService.getNewUsersByYear())
                .build();
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<User>> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<User>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/upload-avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        return ResponseEntity.ok(userService.uploadAvatar(file));
    }

    @PutMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(request));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> logoutAll() {
        return ResponseEntity.ok(userService.logoutAll());
    }

    @GetMapping("/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Address>>> getAddresses() {
        return ResponseEntity.ok(userService.getAddresses());
    }

    @PostMapping("/addresses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Address>> addAddress(@Valid @RequestBody AddAddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.addAddress(request));
    }

    @PutMapping("/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Address>> updateAddress(@PathVariable Long id, @Valid @RequestBody UpdateAddressRequest request) {
        return ResponseEntity.ok(userService.updateAddress(id, request));
    }

    @DeleteMapping("/addresses/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> deleteAddress(@PathVariable Long id) {
        userService.deleteAddress(id);
        return ResponseEntity.ok(ApiResponse.<String>builder().message("Xóa địa chỉ thành công").status(200).build());
    }
}