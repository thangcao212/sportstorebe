package com.sprotshop.sportstore.request; // Thay đổi package nếu cần

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * DTO nhận dữ liệu tạo/cập nhật một danh mục.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryRequest {

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 255, message = "Tên danh mục không được vượt quá 255 ký tự")
    private String name;

    private String description;

    private Long parentId; // null nếu là danh mục gốc

    // For image upload (single image for category)
    private MultipartFile image;

    // For update: optional imageId to delete old image
    private String imageIdToDelete;
}