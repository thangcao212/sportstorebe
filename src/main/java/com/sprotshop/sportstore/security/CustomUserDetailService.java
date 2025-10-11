package com.sprotshop.sportstore.security;

import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository;



    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new NotFoundException("User Email Not Found"));

        return UserPrincipal.create(user);  // 👈 Dùng UserPrincipal thay AuthUser
    }


}