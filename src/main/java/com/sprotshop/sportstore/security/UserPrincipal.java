package com.sprotshop.sportstore.security;

import com.sprotshop.sportstore.Enum.AuthProvider;
import com.sprotshop.sportstore.Enum.UserRole;
import com.sprotshop.sportstore.entity.User;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class UserPrincipal implements UserDetails, OidcUser {  // Giữ implement OidcUser

    private Long id;
    private String email;
    private String displayName;  // 👈 Đổi từ username thành displayName (tên hiển thị)
    private UserRole role;
    private AuthProvider provider;
    private String password;
    private String imageUrl;
    private Map<String, Object> attributes;
    private OidcIdToken idToken;
    private Map<String, Object> claims;

    // Static create for local user
    public static UserPrincipal create(User user) {
        return UserPrincipal.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getUsername())  // 👈 Dùng displayName = user.username (tên)
                .imageUrl(user.getAvatar())
                .role(user.getRole())
                .provider(user.getProvider())
                .password(user.getPassword())
                .build();
    }

    // Static create for OIDC user
    public static UserPrincipal create(User user, Map<String, Object> attributes, OidcIdToken idToken, Map<String, Object> claims) {
        UserPrincipal userPrincipal = create(user);
        userPrincipal.setAttributes(attributes);
        userPrincipal.setIdToken(idToken);
        userPrincipal.setClaims(claims);
        return userPrincipal;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;  // 👈 Giữ nguyên: Trả về email cho UserDetails/JWT validation
    }

    // 👈 Thêm method mới để lấy display name (tên hiển thị)
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }

    // OidcUser methods
    @Override
    public Map<String, Object> getAttributes() { return attributes; }
    @Override
    public Map<String, Object> getClaims() { return claims; }
    @Override
    public OidcIdToken getIdToken() { return idToken; }
    @Override
    public String getName() {
        return displayName;  // 👈 Dùng displayName (tên) thay vì username cũ
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return null;  // Không cần default OidcUserInfo (dùng attributes/claims custom)
    }
}