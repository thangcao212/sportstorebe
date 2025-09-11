package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.Address;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.exception.AlreadyExistsException;
import com.sprotshop.sportstore.exception.InvalidCredentialsException;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.AddressRepository;
import com.sprotshop.sportstore.repository.OrderRepository;
import com.sprotshop.sportstore.repository.UserRepository;
import com.sprotshop.sportstore.request.LoginRequest;
import com.sprotshop.sportstore.request.RegisterRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.security.JwtUtils;

import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final OrderRepository orderRepository;
//    private final CartService cartService;

    @Override
    @Transactional
    public ApiResponse<AuthResponse> register(RegisterRequest registerRequest) {
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new AlreadyExistsException("Email already exists");
        }

        UserRole role = registerRequest.getRole() != null ? registerRequest.getRole() : UserRole.CUSTOMER;

        User user = new User();
        user.setUsername(registerRequest.getName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setPhone(registerRequest.getPhone());
        user.setRole(role);
//        user.setAddresses(new ArrayList<>()); // Có thể bỏ nếu không cần

        User savedUser = userRepository.save(user);

        String token = jwtUtils.generateToken(savedUser.getEmail(), savedUser.getRole().name());

        AuthResponse authResponse = AuthResponse.builder()
                .jwt(token)
                .role(savedUser.getRole())
                .build();

        return ApiResponse.<AuthResponse>builder()
                .message("Đăng ký thành công")
                .data(authResponse)
                .status(HttpStatus.CREATED.value())
                .build();
    }

    @Override
    public ApiResponse<AuthResponse> login(LoginRequest loginRequest) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            // Get user from database
            Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
            if (userOptional.isEmpty()) {
                throw new NotFoundException("User not found");
            }

            User user = userOptional.get();

            // Generate JWT token
            String token = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

            // Create response
            AuthResponse authResponse = AuthResponse.builder()
                    .jwt(token)
                    .role(user.getRole())
                    .build();

            return ApiResponse.<AuthResponse>builder()
                    .message("Đăng nhập thành công")
                    .data(authResponse)
                    .status(HttpStatus.OK.value())
                    .build();
        } catch (BadCredentialsException e) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
    }



    @Override
    public User getCurrentLoggedInUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User Not Found"));

        return user;

    }

    @Override
    public PageResponse<User> getAllUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        return toPageResponse(users);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng với ID: " + id));
        if (orderRepository.existsByUser_Id(id)) {
            throw new RuntimeException("Không thể xóa người dùng vì đã có đơn hàng.");
        }

        userRepository.delete(user);
    }

    private PageResponse<User> toPageResponse(Page<User> page) {
        return PageResponse.<User>builder()
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .data(page.getContent())
                .build();
    }


}
