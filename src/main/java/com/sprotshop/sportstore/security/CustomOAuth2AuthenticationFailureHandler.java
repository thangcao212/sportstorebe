package com.sprotshop.sportstore.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class CustomOAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // 👈 Log full exception + request params để debug
        log.error("OAuth2 failure details: message={}, cause={}, request URI={}, params={}",
                exception.getMessage(),
                exception.getCause() != null ? exception.getCause().getMessage() : "No cause",
                request.getRequestURI(),
                request.getQueryString());

        String errorMsg = exception.getMessage().replace("[", "").replace("]", "");  // Clean "[invalid_request]" thành "invalid_request"
        String targetUrl = "http://localhost:3000/login/failure?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}