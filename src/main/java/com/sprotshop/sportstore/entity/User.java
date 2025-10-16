package com.sprotshop.sportstore.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.sprotshop.sportstore.Enum.AuthProvider;
import com.sprotshop.sportstore.Enum.UserRole;
import jakarta.persistence.*;
import com.sprotshop.sportstore.entity.Cart;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    // Thêm Wishlist (one-to-one, mỗi user có 1 wishlist)
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Wishlist wishlist;

    // Helper method để lấy hoặc tạo wishlist
    @Transient  // Không map vào DB
    public Wishlist getWishlist() {
        if (this.wishlist == null) {
            this.wishlist = Wishlist.builder().user(this).build();  // Tạo mới nếu chưa có
        }
        return this.wishlist;
    }

//    private String providerId;

    private String avatar;

    @Column(name = "avatar_public_id")
    private String avatarPublicId;

    @Column(name = "birthday")
    private LocalDate birthday;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<Address> addresses = new ArrayList<>();


    public void addAddress(Address address) {
        addresses.add(address);
        address.setUser(this);
    }

    public void removeAddress(Address address) {
        addresses.remove(address);
        address.setUser(null);
    }

}
