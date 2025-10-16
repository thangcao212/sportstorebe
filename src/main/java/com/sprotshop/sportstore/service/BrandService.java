package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.BrandRequest;
import com.sprotshop.sportstore.response.BrandResponse;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BrandService {

    public BrandResponse createBrand(BrandRequest request);
    BrandResponse updateBrand(Long brandId, BrandRequest request);
    PageResponse<BrandResponse> getAllBrands(Pageable pageable);
    List<BrandResponse> getAllBrand();
    void deleteBrand(Long brandId);
    BrandResponse getBrandById(Long brandId);

    BrandResponse findOrCreateBrand(String name, String description, String logoUrl);
}
