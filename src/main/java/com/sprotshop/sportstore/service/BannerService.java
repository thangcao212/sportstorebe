package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.BannerRequest;
import com.sprotshop.sportstore.response.BannerResponse;
import com.sprotshop.sportstore.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.List;

public interface BannerService {
    BannerResponse createBanner(BannerRequest bannerRequest) throws IOException;
    BannerResponse updateBanner(Long id, BannerRequest bannerRequest) throws IOException;
    void deleteBanner(Long id) throws IOException;
    BannerResponse getBannerById(Long id);
    Page<BannerResponse> getAllBanners(Pageable pageable);
    Page<BannerResponse> getActiveBanners(Pageable pageable);
}