package com.sprotshop.sportstore.controller; // Thay đổi package nếu cần

import com.sprotshop.sportstore.request.ProductCategoryHierarchyRequest;
import com.sprotshop.sportstore.request.ProductCategoryRequest;
import com.sprotshop.sportstore.response.ApiResponse; // Import ApiResponse của bạn
import com.sprotshop.sportstore.response.PageResponse; // Import PageResponse của bạn
import com.sprotshop.sportstore.response.ProductCategoryResponse;
import com.sprotshop.sportstore.service.ProductCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller quản lý Danh mục sản phẩm.
 */
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ProductCategoryController {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryController.class);
    private final ProductCategoryService productCategoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> createCategory(
            @Valid @RequestBody ProductCategoryRequest categoryRequest) {
        log.info("POST /api/categories - Request: {}", categoryRequest);
        ProductCategoryResponse createdCategory = productCategoryService.createCategory(categoryRequest);
        ApiResponse<ProductCategoryResponse> response = ApiResponse.<ProductCategoryResponse>builder()
                .message("Danh mục đã được tạo thành công.")
                .data(createdCategory)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/hierarchy")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> createCategoryHierarchy(
            @Valid @RequestBody ProductCategoryHierarchyRequest hierarchyRequest) {
        log.info("POST /api/categories/hierarchy - Request: {}", hierarchyRequest.getCategoryNames());
        ProductCategoryResponse createdLeafCategory = productCategoryService.createCategoryHierarchy(hierarchyRequest);
        ApiResponse<ProductCategoryResponse> response = ApiResponse.<ProductCategoryResponse>builder()
                .message("Chuỗi danh mục đã được tạo/xác nhận thành công.")
                .data(createdLeafCategory)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ProductCategoryRequest categoryRequest) {
        log.info("PUT /api/categories/{} - Request: {}", id, categoryRequest);
        ProductCategoryResponse updatedCategory = productCategoryService.updateCategory(id, categoryRequest);
        ApiResponse<ProductCategoryResponse> response = ApiResponse.<ProductCategoryResponse>builder()
                .message("Danh mục đã được cập nhật thành công.")
                .data(updatedCategory)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        log.info("DELETE /api/categories/{}", id);
        productCategoryService.deleteCategory(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Danh mục đã được xóa thành công.")
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductCategoryResponse>> getCategoryById(@PathVariable Long id) {
        log.info("GET /api/categories/{}", id);
        ProductCategoryResponse category = productCategoryService.getCategoryById(id);
        ApiResponse<ProductCategoryResponse> response = ApiResponse.<ProductCategoryResponse>builder()
                .message("Success")
                .data(category)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> getAllCategories() {
        log.info("GET /api/categories");
        List<ProductCategoryResponse> categories = productCategoryService.getAllCategories();
        ApiResponse<List<ProductCategoryResponse>> response = ApiResponse.<List<ProductCategoryResponse>>builder()
                .message("Success")
                .data(categories)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/roots")
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> getRootCategories() {
        log.info("GET /api/categories/roots");
        List<ProductCategoryResponse> rootCategories = productCategoryService.getRootCategories();
        ApiResponse<List<ProductCategoryResponse>> response = ApiResponse.<List<ProductCategoryResponse>>builder()
                .message("Success")
                .data(rootCategories)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{parentId}/children")
    public ResponseEntity<ApiResponse<List<ProductCategoryResponse>>> getChildCategories(
            @PathVariable Long parentId) {
        log.info("GET /api/categories/{}/children", parentId);
        List<ProductCategoryResponse> childCategories = productCategoryService.getChildCategories(parentId);
        ApiResponse<List<ProductCategoryResponse>> response = ApiResponse.<List<ProductCategoryResponse>>builder()
                .message("Success")
                .data(childCategories)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<ProductCategoryResponse>>> getCategoriesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {

        log.info("GET /api/categories/page?page={}&size={}&sort={}&direction={}", page, size, sort, direction);
        Sort.Direction sortDirection = Sort.Direction.ASC;
        try {
            sortDirection = Sort.Direction.fromString(direction.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sort direction '{}'. Defaulting to ASC.", direction);
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        // Service trả về Page<DTO>
        Page<ProductCategoryResponse> categoriesServicePage = productCategoryService.getCategoriesPage(pageable);

        // Tạo PageResponse theo cấu trúc của bạn
        PageResponse<ProductCategoryResponse> pageResponse = PageResponse.<ProductCategoryResponse>builder()
                .data(categoriesServicePage.getContent())
                .currentPage(categoriesServicePage.getNumber())
                .pageSize(categoriesServicePage.getSize())
                .totalElements(categoriesServicePage.getTotalElements())
                .totalPages(categoriesServicePage.getTotalPages())
                .build();

        // Đóng gói PageResponse vào ApiResponse của bạn
        ApiResponse<PageResponse<ProductCategoryResponse>> apiResponse = ApiResponse.<PageResponse<ProductCategoryResponse>>builder()
                .message("Success")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}