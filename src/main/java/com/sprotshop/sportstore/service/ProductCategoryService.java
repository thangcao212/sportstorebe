package com.sprotshop.sportstore.service; // Thay đổi package nếu cần

import com.sprotshop.sportstore.entity.ProductCategory;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.request.ProductCategoryHierarchyRequest;
import com.sprotshop.sportstore.request.ProductCategoryRequest;
import com.sprotshop.sportstore.response.ProductCategoryResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface định nghĩa các dịch vụ quản lý Danh mục sản phẩm.
 */
public interface ProductCategoryService {

    ProductCategoryResponse createCategory(ProductCategoryRequest categoryRequest);

    ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest categoryRequest);

    void deleteCategory(Long id);

    ProductCategoryResponse getCategoryById(Long id);

    List<ProductCategoryResponse> getRootCategories();

    List<ProductCategoryResponse> getChildCategories(Long parentId);

    ProductCategoryResponse createCategoryHierarchy(ProductCategoryHierarchyRequest request);

    Page<ProductCategoryResponse> getCategoriesPage(Pageable pageable);

    List<ProductCategoryResponse> getAllCategories();

    // --- Các hàm helper trả về Entity (dùng nội bộ bởi ProductService) ---
    /** Tìm entity ProductCategory theo ID, ném NotFoundException nếu không thấy. */
    ProductCategory findCategoryEntityById(Long id) throws NotFoundException;

    /** Tìm hoặc tạo mới một danh mục (chỉ một cấp). */
    ProductCategory findOrCreateCategory(String normalizedName, String description, Long parentId);

    /** Tìm hoặc tạo mới một chuỗi danh mục phân cấp và trả về entity lá. */
    ProductCategory findOrCreateCategoryHierarchy(List<@NotBlank String> categoryNames); // <<< ĐÃ THÊM


}