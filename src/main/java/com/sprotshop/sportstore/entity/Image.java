package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String name;
    private String imageUrl;
    private String imageId;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "product_id")
    private Product product;


    public Image(String name, String imageUrl, String imageId, Product product) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.imageId = imageId;
        this.product = product;
    }

    public Image(String imageUrl, Product product) {
        this.imageUrl = imageUrl;

        this.product = product;
    }


}