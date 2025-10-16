package com.sprotshop.sportstore.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {
    private String username;
    private String phone;
    private LocalDate birthday;  // Hoặc String, parse ở service
}
