package com.sprotshop.sportstore.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO nhận tham số tìm kiếm sản phẩm
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequest {

    private String searchValue; // Tìm kiếm theo tên hoặc mô tả

    @Positive(message = "ID danh mục phải lớn hơn 0")
    private Long categoryId; // Danh mục cha (bao gồm con đệ quy)

    @Positive(message = "Giá tối thiểu phải lớn hơn 0")
    private Double minPrice; // Giá tối thiểu

    @Positive(message = "Giá tối đa phải lớn hơn 0")
    private Double maxPrice; // Giá tối đa

    @PositiveOrZero(message = "Tồn kho tối thiểu không được âm")
    private Integer minStock; // Tồn kho tối thiểu

    @PositiveOrZero(message = "Tồn kho tối đa không được âm")
    private Integer maxStock; // Tồn kho tối đa
}