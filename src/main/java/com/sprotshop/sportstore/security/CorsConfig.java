package com.sprotshop.sportstore.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:3000")  // FE origin
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")  // 👈 Thêm PUT/DELETE/OPTIONS
                        // Hoặc dùng .allowedMethods("*") để cho phép tất cả (dễ hơn, nhưng kém secure)
                        .allowedHeaders("Authorization", "Content-Type", "*")  // Đã có, ok
                        .allowCredentials(true)  // Cho phép cookies nếu cần
                        .maxAge(3600);  // Cache preflight 1h
            }
        };
    }
}