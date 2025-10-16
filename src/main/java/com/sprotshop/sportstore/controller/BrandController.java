// BrandController.java - Updated to use Request/Response
package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.BrandRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.BrandResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {
    private final BrandService brandService;

    @PostMapping
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.createBrand(request);
        return ResponseEntity.ok(ApiResponse.success("Thương hiệu được tạo thành công", response));
    }

    @PutMapping("/{brandId}")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(@PathVariable Long brandId, @Valid @RequestBody BrandRequest request) {
        BrandResponse response = brandService.updateBrand(brandId, request);
        return ResponseEntity.ok(ApiResponse.success("Thương hiệu được cập nhật thành công", response));
    }

    @DeleteMapping("/{brandId}")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable Long brandId) {
        brandService.deleteBrand(brandId);
        return ResponseEntity.ok(ApiResponse.success("Thương hiệu được xóa thành công", null));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BrandResponse>>> getAllBrands(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Danh sách thương hiệu", brandService.getAllBrands(pageable)));
    }

    @GetMapping("/{brandId}")
    public ResponseEntity<ApiResponse<BrandResponse>> getBrandById(@PathVariable Long brandId) {
        return ResponseEntity.ok(ApiResponse.success("Chi tiết thương hiệu", brandService.getBrandById(brandId)));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrandsNoPaging() {
        return ResponseEntity.ok(ApiResponse.success("Danh sách tất cả thương hiệu", brandService.getAllBrand()));
    }
}