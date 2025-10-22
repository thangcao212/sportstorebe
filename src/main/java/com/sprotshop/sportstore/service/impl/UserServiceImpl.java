package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.AlreadyExistsException;
import com.sprotshop.sportstore.exception.InvalidCredentialsException;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.*;
import com.sprotshop.sportstore.request.*;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.security.JwtUtils;

import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final OrderRepository orderRepository;
    //    private final JavaMailSender mailSender;
    private final CloudinaryService cloudinaryService;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final DistrictRepository districtRepository;





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
                    .username(user.getUsername())
                   .imageUrl(user.getAvatar())
                    .id(user.getId())
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

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<User> getProfile() {
        User currentUser = getCurrentLoggedInUser();
        Hibernate.initialize(currentUser.getAddresses());  // Load lazy list
        return ApiResponse.<User>builder()
                .message("Lấy thông tin hồ sơ thành công")
                .data(currentUser)
                .status(HttpStatus.OK.value())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<User> updateProfile(UpdateProfileRequest request) {
        User currentUser = getCurrentLoggedInUser();
        currentUser.setUsername(request.getUsername());
        currentUser.setPhone(request.getPhone());
        currentUser.setBirthday(request.getBirthday());
        User updatedUser = userRepository.save(currentUser);
        return ApiResponse.<User>builder()
                .message("Cập nhật hồ sơ thành công")
                .data(updatedUser)
                .status(HttpStatus.OK.value())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<String> uploadAvatar(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File không được rỗng");
        }
        try {
            User currentUser = getCurrentLoggedInUser();

            // Delete old avatar nếu có
            if (currentUser.getAvatarPublicId() != null && !currentUser.getAvatarPublicId().isEmpty()) {
                try {
                    cloudinaryService.delete(currentUser.getAvatarPublicId());
                    log.info("Deleted old avatar for user: {}", currentUser.getEmail());
                } catch (IOException e) {
                    log.error("Failed to delete old avatar: {}", e.getMessage());
                }
            }

            // Upload new
            Map uploadResult = cloudinaryService.upload(file);
            String avatarUrl = (String) uploadResult.get("secure_url");
            String avatarPublicId = (String) uploadResult.get("public_id");

            currentUser.setAvatar(avatarUrl);
            currentUser.setAvatarPublicId(avatarPublicId);
            userRepository.save(currentUser);

            return ApiResponse.<String>builder()
                    .message("Upload avatar thành công")
                    .data(avatarUrl)
                    .status(HttpStatus.OK.value())
                    .build();
        } catch (IOException e) {
            throw new RuntimeException("Upload thất bại: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse<String> changePassword(ChangePasswordRequest request) {
        User currentUser = getCurrentLoggedInUser();
        if (!passwordEncoder.matches(request.getOldPassword(), currentUser.getPassword())) {
            throw new InvalidCredentialsException("Mật khẩu cũ không đúng");
        }
//        if (!isValidPassword(request.getNewPassword())) {
//            throw new IllegalArgumentException("Mật khẩu mới phải >=8 ký tự, có chữ hoa/thường/số");
//        }
        currentUser.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(currentUser);
        return ApiResponse.<String>builder()
                .message("Đổi mật khẩu thành công")
                .status(HttpStatus.OK.value())
                .build();
    }

//    @Override
//    public ApiResponse<String> forgotPassword(ForgotPasswordRequest request) {
//        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
//        if (userOpt.isEmpty()) {
//            throw new NotFoundException("Email không tồn tại");
//        }
//        User user = userOpt.get();
//        String resetToken = jwtUtils.generateResetToken(user.getEmail());  // Exp 1h
//        String resetUrl = "http://localhost:3000/reset-password?token=" + resetToken;
//
//        SimpleMailMessage message = new SimpleMailMessage();
//        message.setTo(user.getEmail());
//        message.setSubject("Reset Password - SportStore");
//        message.setText("Click để reset: " + resetUrl + "\nHết hạn sau 1 giờ.");
//        mailSender.send(message);
//
//        log.info("Reset email sent to: {}", user.getEmail());
//        return ApiResponse.<String>builder()
//                .message("Email reset đã gửi")
//                .status(HttpStatus.OK.value())
//                .build();
//    }

    @Override
    public ApiResponse<String> logoutAll() {
        // Giả sử blacklist JWT hoặc delete refresh tokens (nếu có table)
        log.info("User {} logged out all devices", getCurrentLoggedInUser().getEmail());
        return ApiResponse.<String>builder()
                .message("Đăng xuất tất cả thiết bị thành công")
                .status(HttpStatus.OK.value())
                .build();
    }

    // 👈 Address methods
    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<Address>> getAddresses() {
        User user = getCurrentLoggedInUser();
        Hibernate.initialize(user.getAddresses());
        return ApiResponse.<List<Address>>builder()
                .message("Danh sách địa chỉ")
                .data(user.getAddresses())
                .status(HttpStatus.OK.value())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<Address> addAddress(AddAddressRequest request) {
        User user = getCurrentLoggedInUser();
        // Validate province/district/ward nếu cần (gọi repo findById)
        Province province = provinceRepository.findById(request.getProvinceCode())
                .orElseThrow(() -> new NotFoundException("Mã tỉnh không hợp lệ"));
        District district = districtRepository.findById(request.getDistrictCode())
                .orElseThrow(() -> new NotFoundException("Mã huyện không hợp lệ"));
        Ward ward = wardRepository.findById(request.getWardCode())
                .orElseThrow(() -> new NotFoundException("Mã xã không hợp lệ"));

        String fullAddress = String.format("%s, %s, %s, %s", request.getStreet(), ward.getName(), district.getName(), province.getName());
        Address address = Address.builder()
                .provinceCode(request.getProvinceCode())
                .districtCode(request.getDistrictCode())
                .wardCode(request.getWardCode())
                .street(request.getStreet())
                .fullAddress(fullAddress)
                .label(request.getLabel())
                .isDefault(request.getIsDefault())
                .user(user)
                .build();
        Address saved = addressRepository.save(address);
        user.addAddress(saved);  // Bidirectional
        userRepository.save(user);
        return ApiResponse.<Address>builder()
                .message("Thêm địa chỉ thành công")
                .data(saved)
                .status(HttpStatus.CREATED.value())
                .build();
    }

    @Override
    @Transactional
    public ApiResponse<Address> updateAddress(Long id, UpdateAddressRequest request) {
        User user = getCurrentLoggedInUser();
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Địa chỉ không tồn tại"));

        // 👈 FIXED: Validate và load province/district/ward nếu request có codes mới (hoặc luôn load để rebuild)
        Province province;
        District district;
        Ward ward;

        if (request.getProvinceCode() != null) {
            province = provinceRepository.findById(request.getProvinceCode())
                    .orElseThrow(() -> new NotFoundException("Mã tỉnh không hợp lệ"));
        } else {
            province = provinceRepository.findById(address.getProvinceCode())
                    .orElseThrow(() -> new NotFoundException("Mã tỉnh hiện tại không hợp lệ"));
        }

        if (request.getDistrictCode() != null) {
            district = districtRepository.findById(request.getDistrictCode())
                    .orElseThrow(() -> new NotFoundException("Mã huyện không hợp lệ"));
        } else {
            district = districtRepository.findById(address.getDistrictCode())
                    .orElseThrow(() -> new NotFoundException("Mã huyện hiện tại không hợp lệ"));
        }

        if (request.getWardCode() != null) {
            ward = wardRepository.findById(request.getWardCode())
                    .orElseThrow(() -> new NotFoundException("Mã xã không hợp lệ"));
        } else {
            ward = wardRepository.findById(address.getWardCode())
                    .orElseThrow(() -> new NotFoundException("Mã xã hiện tại không hợp lệ"));
        }

        // 👈 FIXED: Set values (nếu null, giữ nguyên)
        address.setStreet(request.getStreet() != null ? request.getStreet() : address.getStreet());
        address.setProvinceCode(province.getCode());
        address.setDistrictCode(district.getCode());
        address.setWardCode(ward.getCode());

        // 👈 FIXED: Rebuild fullAddress luôn sau khi set codes
        String fullAddress = String.format("%s, %s, %s, %s",
                address.getStreet(), ward.getName(), district.getName(), province.getName());
        address.setFullAddress(fullAddress);

        address.setLabel(request.getLabel() != null ? request.getLabel() : address.getLabel());
        address.setDefault(request.getIsDefault() != null ? request.getIsDefault() : address.isDefault());

        Address updated = addressRepository.save(address);
        return ApiResponse.<Address>builder()
                .message("Cập nhật địa chỉ thành công")
                .data(updated)
                .status(HttpStatus.OK.value())
                .build();
    }
    @Override
    @Transactional
    public void deleteAddress(Long id) {
        User user = getCurrentLoggedInUser();
        Address address = addressRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Địa chỉ không tồn tại"));

        // 👈 NEW: Check nếu địa chỉ đang được dùng trong đơn hàng
        long orderCount = orderRepository.countByAddressId(id);
        if (orderCount > 0) {
            throw new RuntimeException("Không thể xóa địa chỉ này vì nó đang được sử dụng trong " + orderCount + " đơn hàng. Hãy cập nhật đơn hàng trước khi thử lại.");
        }

        // Proceed with deletion
        user.removeAddress(address);  // Bidirectional + orphanRemoval
        userRepository.save(user);  // Trigger delete cascade
    }

    // Existing helpers (getCurrentLoggedInUser, toPageResponse, etc.)
//    private User getCurrentLoggedInUser() {
//        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//        String email = authentication.getName();
//        return userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException("User Not Found"));
//    }

//    private boolean isValidPassword(String password) {
//        return password.length() >= 8 && password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$");
//    }

}