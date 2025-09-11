package com.sprotshop.sportstore.config;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;

@Configuration
public class CacheKeyConfig {

    private final com.sprotshop.sportstore.service.UserService userService;

    public CacheKeyConfig(com.sprotshop.sportstore.service.UserService userService) {
        this.userService = userService;
    }

    @Bean("userOrderKeyGenerator")
    public KeyGenerator userOrderKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            Pageable pageable = (Pageable) params[0]; // param đầu tiên là Pageable
            Long userId = userService.getCurrentLoggedInUser().getId();
            return userId + "-" + pageable.getPageNumber() + "-" + pageable.getPageSize();
        };
    }

    @Bean("allOrderKeyGenerator")
    public KeyGenerator allOrderKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            Pageable pageable = (Pageable) params[0];
            return "all-" + pageable.getPageNumber() + "-" + pageable.getPageSize();
        };
    }
}
