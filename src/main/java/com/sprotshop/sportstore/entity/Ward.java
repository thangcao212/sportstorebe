package com.sprotshop.sportstore.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wards", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ward {
    @Id
    private int code;

    private String name;
    private String codename;

    @Column(name = "short_codename")  // Optional từ API v2
    private String shortCodename;

    @Column(name = "division_type")
    private String divisionType;

//    @ManyToOne
//    @JoinColumn(name = "district_code")
//    private District district;

    @ManyToOne
    @JoinColumn(name = "province_code")
    private Province province;

}