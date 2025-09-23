package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "provinces", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Province {
    @Id
    private int code;

    private String name;
    private String codename;

    @Column(name = "division_type")
    private String divisionType;

    @Column(name = "phone_code")
    private int phoneCode;

    @OneToMany(mappedBy = "province", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<District> districts;
}