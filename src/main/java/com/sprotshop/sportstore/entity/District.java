package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "districts", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class District {
    @Id
    private int code;

    private String name;
    private String codename;

    @Column(name = "division_type")
    private String divisionType;

    @ManyToOne
    @JoinColumn(name = "province_code")
    private Province province;

    @OneToMany(mappedBy = "district", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Ward> wards;
}