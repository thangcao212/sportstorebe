package com.sprotshop.sportstore.exception;

import com.sprotshop.sportstore.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleAllException(Exception ex, WebRequest request) {
        ApiResponse<String> errorResponse = ApiResponse.<String>builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value()) // Thêm status vào response
                .message(ex.getMessage()) // Lấy message từ Exception
                .data(null)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<ApiResponse<String>> handleAlreadyExistsException(AlreadyExistsException ex, WebRequest request) {
        ApiResponse<String> errorResponse = ApiResponse.<String>builder()
                .status(HttpStatus.CONFLICT.value()) // 409 Conflict
                .message(ex.getMessage()) // Lấy message từ exception
                .data(null)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiResponse<String>> handleInvalidCredentialsException(InvalidCredentialsException ex, WebRequest request) {
        ApiResponse<String> errorResponse = ApiResponse.<String>builder()
                .status(HttpStatus.UNAUTHORIZED.value()) // 401 Unauthorized
                .message(ex.getMessage()) // Lấy message từ exception
                .data(null)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotFoundException(NotFoundException ex, WebRequest request) {
        ApiResponse<String> errorResponse = ApiResponse.<String>builder()
                .status(HttpStatus.NOT_FOUND.value()) // 404 Not Found
                .message(ex.getMessage()) // Lấy message từ exception
                .data(null)
                .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }









}
