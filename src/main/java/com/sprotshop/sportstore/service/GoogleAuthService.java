package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.exception.InvalidCredentialsException;
import com.sprotshop.sportstore.repository.UserRepository;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.AuthResponse;
import com.sprotshop.sportstore.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ApiResponse<AuthResponse> handleGoogleLogin(String accessToken) {
        try {
            // Lấy thông tin user từ Google với Bearer header (an toàn hơn query param)
            String googleApiUrl = "https://www.googleapis.com/oauth2/v2/userinfo";
            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken); // Bearer token
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(googleApiUrl, HttpMethod.GET, entity, Map.class);
            Map<String, Object> userInfo = response.getBody();

            if (userInfo == null || !userInfo.containsKey("email")) {
                return ApiResponse.<AuthResponse>error("Không lấy được thông tin người dùng từ Google", 400);
            }

            String email = (String) userInfo.get("email");
            String name = Optional.ofNullable((String) userInfo.get("name")).orElse(email); // Fallback nếu name null

            // Kiểm tra và tạo/update user
            Optional<User> userOptional = userRepository.findByEmail(email);
            User user = userOptional.orElseGet(() -> {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setUsername(name);
                newUser.setPassword(passwordEncoder.encode("google_dummy_" + email)); // Dummy password (không dùng cho login)
                newUser.setPhone(""); // Có thể yêu cầu bổ sung sau
                newUser.setRole(UserRole.CUSTOMER);
                return userRepository.save(newUser);
            });

            // Update name nếu thay đổi
            if (!name.equals(user.getUsername())) {
                user.setUsername(name);
                userRepository.save(user);
            }

            // Tạo JWT
            String jwt = jwtUtils.generateToken(user.getEmail(), user.getRole().name());

            AuthResponse authResponse = AuthResponse.builder()
                    .jwt(jwt)
                    .role(user.getRole())
                    .build();

            return ApiResponse.<AuthResponse>success("Đăng nhập Google thành công", authResponse);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED || e.getStatusCode() == HttpStatus.FORBIDDEN) {
                return ApiResponse.<AuthResponse>error("Token Google không hợp lệ", 401);
            }
            return ApiResponse.<AuthResponse>error("Lỗi xác thực Google: " + e.getMessage(), 400);
        } catch (Exception e) {
            return ApiResponse.<AuthResponse>error("Lỗi server: " + e.getMessage(), 500);
        }
    }
}