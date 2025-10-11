package com.sprotshop.sportstore.security;

import com.sprotshop.sportstore.Enum.AuthProvider;
import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.User;
import com.sprotshop.sportstore.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;  // Request cho OIDC
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;  // 👈 Class implementation (extend này)
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;  // Interface return type
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {  // 👈 Extend class (không implements)

    private final UserRepository userRepository;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) {  // 👈 Override từ parent (OAuth2UserRequest generic)
        try {
            // Delegate to parent (load default OIDC user info)
            OidcUser oidcUser = super.loadUser(userRequest);  // 👈 super thay new

            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            Map<String, Object> attributes = oidcUser.getAttributes();
            Map<String, Object> claims = oidcUser.getClaims();
            OidcIdToken idToken = userRequest.getIdToken();

            String email = (String) attributes.get("email");
            String providerId = oidcUser.getSubject();  // sub từ OidcUser
            String name = (String) attributes.get("name");

            if (email == null || providerId == null) {
                log.error("Missing Google OIDC attributes: email={}, sub={}", email, providerId);
                throw new RuntimeException("Invalid Google OIDC response: Missing email or ID");
            }

            log.info("OIDC login: email={}, providerId={}, name={}", email, providerId, name);

            // Find or create user
            Optional<User> userOptional = userRepository.findByEmail(email);
            User user;
            if (userOptional.isPresent()) {
                user = userOptional.get();
                if (user.getProvider() != AuthProvider.GOOGLE) {
                    throw new RuntimeException("Email đã được đăng ký bằng cách khác. Vui lòng dùng email/password.");
                }
                if (!name.equals(user.getUsername())) {
                    user.setUsername(name);
                    userRepository.save(user);
                }
            } else {
                user = User.builder()
                        .email(email)
                        .username(name)
                        .provider(AuthProvider.GOOGLE)
                        .providerId(providerId)
                        .role(UserRole.CUSTOMER)
                        .phone("")
                        .build();
                userRepository.save(user);
                log.info("Created new user: {}", email);
            }

            // Return custom UserPrincipal as OidcUser
            return UserPrincipal.create(user, attributes, idToken, claims);
        } catch (Exception e) {
            log.error("Error in OIDC user service: {}", e.getMessage(), e);
            throw new RuntimeException("OIDC processing failed", e);
        }
    }
}