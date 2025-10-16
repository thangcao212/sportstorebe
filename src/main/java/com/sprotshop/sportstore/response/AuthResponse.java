package com.sprotshop.sportstore.response;

import com.sprotshop.sportstore.Enum.UserRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthResponse {

    private String jwt;

    // private String message;

    private UserRole role;
    private String username;
    private String imageUrl;
}