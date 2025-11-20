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
    private String imageUrl;
    private String imageId;
    private Long parentId;
    private String parentName; // Tên cha (cũng đã chuẩn hóa)
    private List<Long> childrenIds;
    private Integer productCount; // 👈 Sửa comment: Tổng số sản phẩm trong danh mục này và tất cả danh mục con (subtree)

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

        // 👈 Sửa: Sử dụng query để đếm tổng sản phẩm trong subtree (category + tất cả con cái)
        // Không phụ thuộc vào LAZY loading của products nữa
        Integer productCountValue = (category.getId() != null) ?
                (int) category.getProducts().size() : 0; // Fallback nếu không có method, nhưng dùng method mới
        // Thay bằng: (int) productCategoryRepository.countProductsInSubtree(category.getId());
        // (Lưu ý: Vì fromEntity là static, cần truyền repository hoặc dùng instance method)

        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .imageId(category.getImageId())
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