// BrandServiceImpl.java - Full implementation theo style OrderServiceImpl
package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Brand;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.request.BrandRequest;
import com.sprotshop.sportstore.response.BrandResponse;
import com.sprotshop.sportstore.response.PageResponse;
import com.sprotshop.sportstore.repository.BrandRepository;
import com.sprotshop.sportstore.service.BrandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional
    @CacheEvict(value = "allBrands", allEntries = true)
    public BrandResponse createBrand(BrandRequest request) {
        try {
            log.info("Creating brand: {}", request.getName());
            Brand brand = Brand.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .logoUrl(request.getLogoUrl())
                    .build();
            Brand savedBrand = brandRepository.save(brand);
            log.info("Brand created successfully: {}", savedBrand.getId());
            return BrandResponse.fromEntity(savedBrand);
        } catch (Exception e) {
            log.error("Create brand failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allBrands", "brandById"}, key = "#brandId")
    public BrandResponse updateBrand(Long brandId, BrandRequest request) {
        try {
            log.info("Updating brandId: {}", brandId);
            Brand brand = brandRepository.findById(brandId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu: " + brandId));
            brand.setName(request.getName());
            brand.setDescription(request.getDescription());
            brand.setLogoUrl(request.getLogoUrl());
            Brand updatedBrand = brandRepository.save(brand);
            log.info("Brand updated successfully: {}", brandId);
            return BrandResponse.fromEntity(updatedBrand);
        } catch (Exception e) {
            log.error("Update brand failed for brandId {}: {}", brandId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = {"allBrands", "brandById"}, allEntries = true)
    public void deleteBrand(Long brandId) {
        try {
            log.info("Deleting brandId: {}", brandId);
            Brand brand = brandRepository.findById(brandId)
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu: " + brandId));

            // Nếu có quan hệ với Product, nên kiểm tra kỹ
            if (brand.getProducts() != null && !brand.getProducts().isEmpty()) {
                throw new IllegalStateException("Không thể xóa thương hiệu đang có sản phẩm liên kết");
            }

            brandRepository.delete(brand);
            log.info("✅ Brand deleted successfully: {}", brandId);
        } catch (Exception e) {
            log.error("❌ Delete brand failed for brandId {}: {}", brandId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BrandResponse> getAllBrands(Pageable pageable) {
        log.info("Fetching all brands");
        Page<Brand> brands = brandRepository.findAll(pageable);
        return PageResponse.fromPage(brands.map(BrandResponse::fromEntity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrand() {
        log.info("Fetching all brands (no paging)");
        List<Brand> brands = brandRepository.findAll();
        return brands.stream()
                .map(BrandResponse::fromEntity)
                .collect(Collectors.toList());
    }
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "brandById", key = "#brandId")
    public BrandResponse getBrandById(Long brandId) {
        log.info("Fetching brandId: {}", brandId);
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy thương hiệu: " + brandId));
        return BrandResponse.fromEntity(brand);
    }

    @Override
    @Transactional
    public BrandResponse findOrCreateBrand(String name, String description, String logoUrl) {
        log.info("Finding or creating brand: {}", name);
        Optional<Brand> existing = brandRepository.findByNameIgnoreCase(name);
        if (existing.isPresent()) {
            log.info("Brand already exists: {}", name);
            return BrandResponse.fromEntity(existing.get());
        }

        BrandRequest request = BrandRequest.builder()
                .name(name)
                .description(description)
                .logoUrl(logoUrl)
                .build();
        return createBrand(request);
    }
}