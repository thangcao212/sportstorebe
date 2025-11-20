package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Banner;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.BannerRepository;
import com.sprotshop.sportstore.request.BannerRequest;
import com.sprotshop.sportstore.response.BannerResponse;
import com.sprotshop.sportstore.service.BannerService;
import com.sprotshop.sportstore.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public BannerResponse createBanner(BannerRequest bannerRequest) throws IOException {
        log.info("Creating banner: {}", bannerRequest.getTitle());

        Banner banner = Banner.builder()
                .title(bannerRequest.getTitle())
                .subtitle(bannerRequest.getSubtitle())
                .description(bannerRequest.getDescription())
                .buttonText(bannerRequest.getButtonText())
                .buttonColor(bannerRequest.getButtonColor())
                .linkUrl(bannerRequest.getLinkUrl())
                .active(bannerRequest.getActive() != null ? bannerRequest.getActive() : true)
                .displayOrder(bannerRequest.getDisplayOrder() != null ? bannerRequest.getDisplayOrder() : 0)
                .build();

        // Process image upload if provided
        processImageUpload(bannerRequest.getImage(), banner, true);

        Banner savedBanner = bannerRepository.save(banner);
        log.info("Banner created successfully with id: {}", savedBanner.getId());

        return BannerResponse.fromEntity(savedBanner);
    }

    @Override
    @Transactional
    public BannerResponse updateBanner(Long id, BannerRequest bannerRequest) throws IOException {
        log.info("Updating banner id: {}", id);
        Banner banner = findBannerEntityById(id);

        // Update fields
        if (StringUtils.hasText(bannerRequest.getTitle())) {
            banner.setTitle(bannerRequest.getTitle());
        }
        if (StringUtils.hasText(bannerRequest.getSubtitle())) {
            banner.setSubtitle(bannerRequest.getSubtitle());
        }
        if (StringUtils.hasText(bannerRequest.getDescription())) {
            banner.setDescription(bannerRequest.getDescription());
        }
        if (StringUtils.hasText(bannerRequest.getButtonText())) {
            banner.setButtonText(bannerRequest.getButtonText());
        }
        if (StringUtils.hasText(bannerRequest.getButtonColor())) {
            banner.setButtonColor(bannerRequest.getButtonColor());
        }
        if (StringUtils.hasText(bannerRequest.getLinkUrl())) {
            banner.setLinkUrl(bannerRequest.getLinkUrl());
        }
        if (bannerRequest.getActive() != null) {
            banner.setActive(bannerRequest.getActive());
        }
        if (bannerRequest.getDisplayOrder() != null) {
            banner.setDisplayOrder(bannerRequest.getDisplayOrder());
        }

        // Handle image update: delete old if specified, upload new if provided
        if (StringUtils.hasText(bannerRequest.getImageIdToDelete()) &&
                Objects.equals(bannerRequest.getImageIdToDelete(), banner.getImageId())) {
            deleteImageFromCloudinary(banner.getImageId());
            banner.setImageUrl(null);
            banner.setImageId(null);
            log.info("Old image deleted for banner id: {}", id);
        }
        processImageUpload(bannerRequest.getImage(), banner, false);

        Banner updatedBanner = bannerRepository.save(banner);
        log.info("Banner updated successfully with id: {}", updatedBanner.getId());

        return BannerResponse.fromEntity(updatedBanner);
    }

    @Override
    @Transactional
    public void deleteBanner(Long id) throws IOException {
        log.warn("Deleting banner id: {}", id);
        Banner banner = findBannerEntityById(id);

        // Delete image from Cloudinary if exists
        if (StringUtils.hasText(banner.getImageId())) {
            deleteImageFromCloudinary(banner.getImageId());
            log.info("Image deleted from Cloudinary for banner id: {}", id);
        }

        bannerRepository.delete(banner);
        log.info("Banner deleted successfully with id: {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public BannerResponse getBannerById(Long id) {
        log.debug("Fetching banner by id: {}", id);
        Banner banner = findBannerEntityById(id);
        return BannerResponse.fromEntity(banner);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BannerResponse> getAllBanners(Pageable pageable) {
        log.debug("Fetching all banners with pageable: {}", pageable);
        Page<Banner> bannerPage = bannerRepository.findAllOrdered(pageable);
        return bannerPage.map(BannerResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BannerResponse> getActiveBanners(Pageable pageable) {
        log.debug("Fetching active banners with pageable: {}", pageable);
        Page<Banner> bannerPage = bannerRepository.findActiveBannersOrdered(pageable);
        return bannerPage.map(BannerResponse::fromEntity);
    }

    // Helper: Find banner by ID
    private Banner findBannerEntityById(Long id) {
        return bannerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Banner not found with ID: " + id));
    }

    // Helper: Process image upload (create or update)
    private void processImageUpload(MultipartFile imageFile, Banner banner, boolean isCreate) throws IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            log.info("Processing image upload for banner: {}", banner.getTitle() != null ? banner.getTitle() : "NEW");
            // Delete old image if updating and new image provided
            if (!isCreate && StringUtils.hasText(banner.getImageId())) {
                deleteImageFromCloudinary(banner.getImageId());
                log.info("Old image replaced for banner id: {}", banner.getId());
            }
            // Upload new image
            Map uploadResult = cloudinaryService.upload(imageFile);
            String imageUrl = (String) uploadResult.get("secure_url");
            String imageId = (String) uploadResult.get("public_id");
            banner.setImageUrl(imageUrl);
            banner.setImageId(imageId);
            log.debug("Image uploaded: URL={}, ID={} for banner", imageUrl, imageId);
        }
    }

    // Helper: Delete image from Cloudinary
    private void deleteImageFromCloudinary(String imageId) throws IOException {
        if (StringUtils.hasText(imageId)) {
            try {
                cloudinaryService.delete(imageId);
                log.debug("Cloudinary image deleted: {}", imageId);
            } catch (IOException e) {
                log.error("Failed to delete Cloudinary image (id={}): {}", imageId, e.getMessage());
                throw e; // Re-throw to handle in caller
            }
        }
    }
}