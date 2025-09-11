package com.sprotshop.sportstore.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sprotshop.sportstore.Enum.UserRole;
import jakarta.persistence.*;
import com.sprotshop.sportstore.entity.Cart;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String password;
    private String phone;

    @Enumerated(EnumType.STRING)
    private UserRole role;

//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<Address> addresses;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Cart cart;
}
