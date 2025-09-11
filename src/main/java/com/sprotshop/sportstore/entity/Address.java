package com.sprotshop.sportstore.entity;

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
    private int provinceCode;

    @Column(name = "district_code")
    private int districtCode;

    @Column(name = "ward_code")
    private int wardCode;

    private String street;

    @Column(name = "full_address")
    private String fullAddress;
}



