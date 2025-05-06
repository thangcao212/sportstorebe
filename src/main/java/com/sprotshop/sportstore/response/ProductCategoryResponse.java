package com.sprotshop.sportstore.response; // Thay đổi package nếu cần

import com.sprotshop.sportstore.entity.ProductCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
// Import Hibernate để kiểm tra LAZY loading (nếu cần thiết)
import org.hibernate.Hibernate;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO chứa thông tin danh mục trả về cho client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryResponse {

    private Long id;
    private String name; // Tên đã chuẩn hóa (lowercase)
    private String description;
    private Long parentId;
    private String parentName; // Tên cha (cũng đã chuẩn hóa)
    private List<Long> childrenIds;
    private Integer productCount; // Có thể null nếu không tính toán được do LAZY loading

    public static ProductCategoryResponse fromEntity(ProductCategory category) {
        if (category == null) {
            return null;
        }
        ProductCategory parent = category.getParent();
        Long parentIdValue = (parent != null) ? parent.getId() : null;
        String parentNameValue = (parent != null) ? parent.getName() : null;

        List<Long> childIdsValue = (category.getChildren() != null) ?
                category.getChildren().stream().map(ProductCategory::getId).collect(Collectors.toList()) :
                Collections.emptyList();

        // Lấy productCount một cách an toàn, trả về null nếu collection chưa được load
        Integer productCountValue = (category.getProducts() != null && !Hibernate.isInitialized(category.getProducts()))
                ? null // Trả về null nếu collection LAZY chưa được load
                : (category.getProducts() != null ? category.getProducts().size() : 0);


        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .parentId(parentIdValue)
                .parentName(parentNameValue)
                .childrenIds(childIdsValue)
                .productCount(productCountValue)
                .build();
    }

    public static List<ProductCategoryResponse> fromEntities(List<ProductCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream()
                .map(ProductCategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }
}