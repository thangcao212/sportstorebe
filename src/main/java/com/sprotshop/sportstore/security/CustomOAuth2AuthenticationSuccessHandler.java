package com.sprotshop.sportstore.security;

import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomOAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;  // Chỉ cần JwtUtils để generate token

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        // Generate JWT trực tiếp từ UserPrincipal (không cần UserService)
        String token = jwtUtils.generateToken(userPrincipal.getEmail(), userPrincipal.getRole().name());

        AuthResponse authResponse = AuthResponse.builder()
                .jwt(token)
                .role(userPrincipal.getRole())
                .username(userPrincipal.getDisplayName())  // 👈 Fix: Dùng displayName (tên) thay vì getUsername() (email)
                .imageUrl(userPrincipal.getImageUrl())
                .build();

        // Redirect về frontend với query param
        String targetUrl = "http://localhost:3000/login/success?" +
                "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8) +
                "&role=" + URLEncoder.encode(userPrincipal.getRole().name(), StandardCharsets.UTF_8)+
                "&username=" + URLEncoder.encode(userPrincipal.getDisplayName(), StandardCharsets.UTF_8) +  // 👈 Fix: Dùng displayName (tên)
                "&imageUrl=" + URLEncoder.encode(
                userPrincipal.getImageUrl() != null ? userPrincipal.getImageUrl() : "",
                StandardCharsets.UTF_8
        );

        log.info("OAuth2 success redirect: {}", targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}