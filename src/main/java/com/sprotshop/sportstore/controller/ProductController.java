package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.ProductRequest;
import com.sprotshop.sportstore.request.ProductSearchRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.response.ProductResponse;
import com.sprotshop.sportstore.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @ModelAttribute ProductRequest productRequest) throws IOException {
        log.info("POST /api/products - Creating product: {}", productRequest.getName());
        ProductResponse createdProduct = productService.createProduct(productRequest);
        ApiResponse<ProductResponse> response = ApiResponse.<ProductResponse>builder()
                .message("Product created successfully")
                .data(createdProduct)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @ModelAttribute ProductRequest productRequest) throws IOException {
        log.info("PUT /api/products/{} - Updating product", id);
        ProductResponse updatedProduct = productService.updateProduct(id, productRequest);
        ApiResponse<ProductResponse> response = ApiResponse.<ProductResponse>builder()
                .message("Product updated successfully")
                .data(updatedProduct)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) throws IOException {
        log.warn("DELETE /api/products/{} - Deleting product", id);
        productService.deleteProduct(id);
        ApiResponse<Void> response = ApiResponse.<Void>builder()
                .message("Product deleted successfully")
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        log.info("GET /api/products/{}", id);
        ProductResponse product = productService.getProductById(id);
        ApiResponse<ProductResponse> response = ApiResponse.<ProductResponse>builder()
                .message("Success")
                .data(product)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getAllProducts() {
        log.info("GET /api/products");
        List<ProductResponse> products = productService.getAllProducts();
        ApiResponse<List<ProductResponse>> response = ApiResponse.<List<ProductResponse>>builder()
                .message("Success")
                .data(products)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getProductsByCategory(
            @PathVariable Long categoryId) {
        log.info("GET /api/products/category/{}", categoryId);
        List<ProductResponse> products = productService.getProductsByCategory(categoryId);
        ApiResponse<List<ProductResponse>> response = ApiResponse.<List<ProductResponse>>builder()
                .message("Products for category ID " + categoryId)
                .data(products)
                .status(HttpStatus.OK.value())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/page")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProductsPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sort,
            @RequestParam(defaultValue = "ASC") String direction) {

        log.info("GET /api/products/page?page={}&size={}&sort={}&direction={}", page, size, sort, direction);
        Sort.Direction sortDirection = Sort.Direction.ASC;
        try { sortDirection = Sort.Direction.fromString(direction.toUpperCase()); }
        catch (IllegalArgumentException e) { log.warn("Invalid sort direction '{}'. Defaulting to ASC.", direction); }
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<ProductResponse> productsServicePage = productService.getProductsPage(pageable);

        PageResponse<ProductResponse> pageResponse = PageResponse.<ProductResponse>builder()
                .data(productsServicePage.getContent())
                .currentPage(productsServicePage.getNumber())
                .pageSize(productsServicePage.getSize())
                .totalElements(productsServicePage.getTotalElements())
                .totalPages(productsServicePage.getTotalPages())
                .build();

        ApiResponse<PageResponse<ProductResponse>> apiResponse = ApiResponse.<PageResponse<ProductResponse>>builder()
                .message("Success")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @RequestParam(required = false) String searchValue,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer minStock,
            @RequestParam(required = false) Integer maxStock,
            Pageable pageable) {
        log.debug("Received request to search products with criteria: searchValue={}, categoryId={}, minPrice={}, maxPrice={}, minStock={}, maxStock={}, pageable={}",
                searchValue, categoryId, minPrice, maxPrice, minStock, maxStock, pageable);

        ProductSearchRequest searchRequest = ProductSearchRequest.builder()
                .searchValue(searchValue)
                .categoryId(categoryId)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .minStock(minStock)
                .maxStock(maxStock)
                .build();

        Page<ProductResponse> page = productService.searchProducts(searchRequest, pageable);
        PageResponse<ProductResponse> pageResponse = PageResponse.<ProductResponse>builder()
                .currentPage(page.getNumber())
                .totalPages(page.getTotalPages())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .data(page.getContent())
                .build();
        ApiResponse<PageResponse<ProductResponse>> apiResponse = ApiResponse.<PageResponse<ProductResponse>>builder()
                .message("Products searched successfully")
                .data(pageResponse)
                .status(HttpStatus.OK.value())
                .build();
        log.info("Found {} products on page {} of size {} matching search criteria",
                page.getTotalElements(), page.getNumber(), page.getSize());
        return ResponseEntity.ok(apiResponse);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> importProducts(
            @RequestParam("file") MultipartFile excelFile) throws IOException {
        log.info("POST /api/products/import - Importing products from Excel: {}", excelFile.getOriginalFilename());
        List<ProductResponse> importedProducts = productService.importProductsFromExcel(excelFile);
        ApiResponse<List<ProductResponse>> response = ApiResponse.<List<ProductResponse>>builder()
                .message("Products imported successfully: " + importedProducts.size() + " items")
                .data(importedProducts)
                .status(HttpStatus.CREATED.value())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}