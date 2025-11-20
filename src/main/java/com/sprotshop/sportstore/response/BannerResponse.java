package com.sprotshop.sportstore.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BannerResponse {
    private Long id;
    private String imageUrl;
    private String imageId;
    private String title;
    private String subtitle;
    private String description;
    private String buttonText;
    private String buttonColor;
    private String linkUrl;
    private Boolean active;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BannerResponse fromEntity(com.sprotshop.sportstore.entity.Banner banner) {
        if (banner == null) {
            return null;
        }
        return BannerResponse.builder()
                .id(banner.getId())
                .imageUrl(banner.getImageUrl())
                .imageId(banner.getImageId())
                .title(banner.getTitle())
                .subtitle(banner.getSubtitle())
                .description(banner.getDescription())
                .buttonText(banner.getButtonText())
                .buttonColor(banner.getButtonColor())
                .linkUrl(banner.getLinkUrl())
                .active(banner.getActive())
                .displayOrder(banner.getDisplayOrder())
                .createdAt(banner.getCreatedAt())
                .updatedAt(banner.getUpdatedAt())
                .build();
    }

    public static java.util.List<BannerResponse> fromEntities(java.util.List<com.sprotshop.sportstore.entity.Banner> banners) {
        return banners.stream()
                .map(BannerResponse::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }
}