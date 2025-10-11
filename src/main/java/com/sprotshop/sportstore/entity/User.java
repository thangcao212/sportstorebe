package com.sprotshop.sportstore.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sprotshop.sportstore.Enum.AuthProvider;
import com.sprotshop.sportstore.Enum.UserRole;
import jakarta.persistence.*;
import com.sprotshop.sportstore.entity.Cart;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user")
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;

    @Column(nullable = true)  // 👈 Nullable cho social user
    private String password;
    private String phone;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.CUSTOMER;

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<Address> addresses;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Cart cart;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)  // 👈 Thêm
    private AuthProvider provider = AuthProvider.LOCAL;  // Default local

    private String providerId;  // 👈 Thêm, ID từ Google (sub)


}
