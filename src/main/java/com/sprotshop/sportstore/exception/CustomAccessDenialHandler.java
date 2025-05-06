package com.sprotshop.sportstore.exception;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprotshop.sportstore.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;


@Component
@Slf4j
public class CustomAccessDenialHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("Forbidden request: {}", request.getRequestURI());

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403

        ApiResponse<String> apiResponse = ApiResponse.<String>builder()
                .status(HttpServletResponse.SC_FORBIDDEN) // 403
                .message(accessDeniedException.getMessage()) // Lấy message từ exception
                .data(null)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        PrintWriter writer = response.getWriter();
        writer.write(objectMapper.writeValueAsString(apiResponse));
        writer.flush();
    }
}