package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<User>>> getAllProducts(
            Pageable pageable) {
        PageResponse<User> products = userService.getAllUsers(pageable);

        ApiResponse<PageResponse<User>> response = ApiResponse.<PageResponse<User>>builder()
                .status(HttpStatus.OK.value())
                .message("Danh sách người dùng !")
                .data(products)
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



}
