package com.sprotshop.sportstore.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BannerRequest {
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private String buttonColor;
    private String linkUrl;
    private Boolean active;
    private Integer displayOrder;

    // For image upload (single image for banner)
    private MultipartFile image;

    // For update: optional imageId to delete old image
    private String imageIdToDelete;
}