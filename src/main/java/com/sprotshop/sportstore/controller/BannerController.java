package com.sprotshop.sportstore.controller;

import com.sprotshop.sportstore.request.BannerRequest;
import com.sprotshop.sportstore.response.ApiResponse;
import com.sprotshop.sportstore.response.BannerResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.service.BannerService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(@ModelAttribute BannerRequest bannerRequest) {
        try {
            BannerResponse response = bannerService.createBanner(bannerRequest);
            return ResponseEntity.ok(ApiResponse.success("Banner created successfully", response));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to create banner: " + e.getMessage(), 500));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(
            @PathVariable Long id,
            @ModelAttribute BannerRequest bannerRequest) {
        try {
            BannerResponse response = bannerService.updateBanner(id, bannerRequest);
            return ResponseEntity.ok(ApiResponse.success("Banner updated successfully", response));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update banner: " + e.getMessage(), 500));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long id) {
        try {
            bannerService.deleteBanner(id);
            return ResponseEntity.ok(ApiResponse.success("Banner deleted successfully", null));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to delete banner: " + e.getMessage(), 500));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BannerResponse>> getBannerById(@PathVariable Long id) {
        BannerResponse response = bannerService.getBannerById(id);
        return ResponseEntity.ok(ApiResponse.success("Banner retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<BannerResponse>>> getAllBanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder,asc") String sortBy) {
        Pageable pageable = buildPageable(page, size, sortBy);
        var bannerPage = bannerService.getAllBanners(pageable);
        var pageResponse = PageResponse.fromPage(bannerPage);
        return ResponseEntity.ok(ApiResponse.success("All banners retrieved successfully", pageResponse));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<PageResponse<BannerResponse>>> getActiveBanners(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "displayOrder,asc") String sortBy) {
        Pageable pageable = buildPageable(page, size, sortBy);
        var bannerPage = bannerService.getActiveBanners(pageable);
        var pageResponse = PageResponse.fromPage(bannerPage);
        return ResponseEntity.ok(ApiResponse.success("Active banners retrieved successfully", pageResponse));
    }

    private Pageable buildPageable(int page, int size, String sortBy) {
        try {
            if (sortBy.contains(",")) {
                String[] parts = sortBy.split(",");
                String property = parts[0].trim();
                Sort.Direction direction = Sort.Direction.fromString(parts[1].trim().toUpperCase());
                return PageRequest.of(page, size, Sort.by(direction, property));
            } else {
                return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, sortBy));
            }
        } catch (IllegalArgumentException e) {
            // Fallback to default sort
            return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "displayOrder"));
        }
    }
}