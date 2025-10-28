package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sprotshop.sportstore.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "province_code")
    private Integer provinceCode;

//    @Column(name = "district_code")
//    private Integer districtCode;

    @Column(name = "ward_code")
    private Integer wardCode;

    private String street;

    @Column(name = "full_address")
    private String fullAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    @Column(name = "is_default")
    private boolean isDefault = false;

    @Column(name = "label")
    private String label;
}