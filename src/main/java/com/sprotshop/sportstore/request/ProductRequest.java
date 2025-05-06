package com.sprotshop.sportstore.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Giá sản phẩm không được để trống")
    @Positive(message = "Giá sản phẩm phải lớn hơn 0")
    private Double price;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @PositiveOrZero(message = "Số lượng tồn kho không được âm")
    private Integer stockQuantity;

    // --- Category Info (Choose one method) ---
    private Long categoryId; // Priority 1: Existing category ID

    @Size(min = 1, message = "Đường dẫn danh mục phải có ít nhất 1 tên")
    private List<@NotBlank(message = "Tên danh mục trong đường dẫn không được trống") String> categoryPath; // Priority 2: Find/Create hierarchy path

    private String categoryName; // Priority 3: Find/Create single level name
    private String categoryDescription; // Used with categoryName or categoryPath (for leaf)
    private Long parentCategoryId; // Used with categoryName for single level creation

    // --- Image Info ---
    private List<MultipartFile> images; // New images to upload
    private List<String> imageIdsToDelete; // Cloudinary Public IDs of images to delete on update
}