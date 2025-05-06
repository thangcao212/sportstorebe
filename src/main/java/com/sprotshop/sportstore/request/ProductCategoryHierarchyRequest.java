package com.sprotshop.sportstore.request; // Thay đổi package nếu cần

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO nhận dữ liệu tạo chuỗi danh mục phân cấp.
 */
@Data
@NoArgsConstructor
public class ProductCategoryHierarchyRequest {

    @NotEmpty(message = "Danh sách tên danh mục không được rỗng")
    private List<@NotBlank(message = "Tên danh mục không được để trống") @Size(max = 255) String> categoryNames;
}