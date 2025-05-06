package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductCategory;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.ImageRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.request.ProductRequest;
import com.sprotshop.sportstore.response.ProductResponse;
import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.ProductCategoryService; // Interface
import com.sprotshop.sportstore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate; // Import Hibernate
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException; // Import nếu dùng trong resolveCategory
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils; // Import CollectionUtils
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final ProductCategoryService productCategoryService; // Dùng Interface
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) throws IOException {
        log.info("Creating product: {}", productRequest.getName());

        // 1. Xác định Category (dùng logic cũ với hàm helper mới)
        ProductCategory category = resolveCategoryFromRequest(productRequest);
        if (category == null) {
            throw new IllegalArgumentException("Category information is required and could not be resolved.");
        }
        log.info("Resolved category for new product: id={}, name='{}'", category.getId(), category.getName());

        // 2. Tạo Product entity ban đầu
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .stockQuantity(productRequest.getStockQuantity())
                .productCategory(category)
                .images(new ArrayList<>())
                .build();

        // 3. Xử lý upload ảnh và thêm vào Product entity
        processImageUploads(productRequest.getImages(), product);

        // 4. Lưu sản phẩm lần đầu (cascade lưu ảnh)
        Product savedProductInitial = productRepository.save(product);
        log.info("Product saved initially, id={}", savedProductInitial.getId());

        // 5. *** TẢI LẠI PRODUCT BẰNG findById ĐỂ ĐẢM BẢO LOAD ĐỦ DỮ LIỆU ***
        //    (findById trong repo đã có @EntityGraph load sẵn images và category)
        Product fullyLoadedProduct = findProductEntityById(savedProductInitial.getId());
        log.info("Product fully reloaded after save, id={}", fullyLoadedProduct.getId());

        // 6. Convert DTO từ entity đã được load đầy đủ và trả về
        return ProductResponse.fromEntity(fullyLoadedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) throws IOException {
        log.info("Updating product id: {}", id);
        Product product = findProductEntityById(id); // Dùng hàm tìm kèm load EAGER

        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
        product.setStockQuantity(productRequest.getStockQuantity());
        log.debug("Basic info updated for product id: {}", id);

        updateProductCategoryIfNeeded(product, productRequest); // Dùng helper mới
        processImageDeletions(productRequest.getImageIdsToDelete(), product);
        processImageUploads(productRequest.getImages(), product);

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: id={}", updatedProduct.getId());
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) throws IOException {
        log.warn("Deleting product id: {}", id);
        Product product = findProductEntityById(id); // Load kèm ảnh

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            log.info("Deleting {} images from Cloudinary for product id: {}", product.getImages().size(), id);
            List<String> imageIds = product.getImages().stream()
                    .map(Image::getImageId).filter(Objects::nonNull).toList();
            for (String imageId : imageIds) {
                try { cloudinaryService.delete(imageId); }
                catch (IOException e) { log.error("Failed to delete Cloudinary image (id={}): {}", imageId, e.getMessage()); }
            }
        }
        productRepository.delete(product);
        log.warn("Product deleted successfully from DB: id={}", id);
    }

    // --- Các hàm GET không đổi logic nhiều ---
    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product by id: {}", id);
        Product product = findProductEntityById(id);
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        log.debug("Fetching all products");
        return ProductResponse.fromEntities(productRepository.findAll()); // Repo đã có @EntityGraph
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsPage(Pageable pageable) {
        log.debug("Fetching products page: {}", pageable);
        return productRepository.findAll(pageable).map(ProductResponse::fromEntity); // Repo đã có @EntityGraph
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProductsByName(String name) {
        log.debug("Searching products by name containing: '{}'", name);
        return ProductResponse.fromEntities(productRepository.findByNameContainingIgnoreCase(name)); // Repo đã có @EntityGraph
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products by category id: {}", categoryId);
        ProductCategory category = productCategoryService.findCategoryEntityById(categoryId); // Chỉ tìm, không tạo
        return ProductResponse.fromEntities(productRepository.findByProductCategory(category)); // Repo đã có @EntityGraph
    }

    // --- Helper Methods ---

    /** Tìm Product kèm Category và Images bằng findById đã override với EntityGraph */
    private Product findProductEntityById(Long id){
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + id));
    }

    /** Helper mới: Xác định Category từ Request theo thứ tự ưu tiên */
    private ProductCategory resolveCategoryFromRequest(ProductRequest request) {
        log.debug("Resolving category from request...");
        if (request.getCategoryId() != null) {
            log.debug("Using categoryId: {}", request.getCategoryId());
            return productCategoryService.findCategoryEntityById(request.getCategoryId());
        } else if (!CollectionUtils.isEmpty(request.getCategoryPath())) {
            log.debug("Using categoryPath: {}", request.getCategoryPath());
            return productCategoryService.findOrCreateCategoryHierarchy(request.getCategoryPath());
        } else if (StringUtils.hasText(request.getCategoryName())) {
            log.warn("Using single-level find/create from categoryName: '{}', parentId: {}", request.getCategoryName().toLowerCase(), request.getParentCategoryId());
            return productCategoryService.findOrCreateCategory(
                    request.getCategoryName().toLowerCase(),
                    request.getCategoryDescription(),
                    request.getParentCategoryId()
            );
        } else {
            log.error("No valid category information provided.");
            throw new IllegalArgumentException("Category information (categoryId, categoryPath or categoryName) is required.");
            // return null; // Nếu cho phép sản phẩm không có category
        }
    }

    /** Helper mới: Cập nhật category nếu cần */
    private void updateProductCategoryIfNeeded(Product product, ProductRequest request) {
        // Chỉ xử lý nếu có thông tin category trong request
        if (request.getCategoryId() != null || !CollectionUtils.isEmpty(request.getCategoryPath()) || StringUtils.hasText(request.getCategoryName())) {
            ProductCategory newCategory = resolveCategoryFromRequest(request);
            ProductCategory currentCategory = product.getProductCategory();
            // Check if category actually changed
            if (currentCategory == null || !Objects.equals(currentCategory.getId(), newCategory.getId())) {
                product.setProductCategory(newCategory);
                log.info("Updated category for product id={} to category id={}", product.getId(), newCategory.getId());
            }
        }
        // Thêm logic xóa category khỏi product nếu cần:
        // else if (product.getProductCategory() != null) {
        //     log.info("Removing category from product id={}", product.getId());
        //     product.setProductCategory(null);
        // }
    }

    /** Helper: Xử lý upload ảnh */
    private void processImageUploads(List<MultipartFile> imageFiles, Product product) throws IOException {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            log.info("Processing {} new image uploads for product id={}", imageFiles.size(), product.getId());
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    Image image = uploadAndCreateImageEntity(file);
                    product.addImage(image); // Dùng helper để set quan hệ 2 chiều
                }
            }
        }
    }

    /** Helper: Xử lý xóa ảnh */
    private void processImageDeletions(List<String> imageIdsToDelete, Product product) {
        if (imageIdsToDelete != null && !imageIdsToDelete.isEmpty()) {
            log.info("Processing {} image deletions for product id={}", imageIdsToDelete.size(), product.getId());
            List<Image> imagesToRemove = new ArrayList<>();
            // Tìm các ảnh cần xóa trong list ảnh hiện tại của product (đã được EAGER load)
            product.getImages().stream()
                    .filter(img -> imageIdsToDelete.contains(img.getImageId()))
                    .forEach(imagesToRemove::add);


            if (!imagesToRemove.isEmpty()) {
                log.debug("Found {} images associated with product to delete.", imagesToRemove.size());
                for (Image img : imagesToRemove) {
                    log.debug("Deleting image: dbId={}, cloudinaryId={}", img.getId(), img.getImageId());
                    try {
                        cloudinaryService.delete(img.getImageId());
                    } catch (IOException e) {
                        log.error("Failed to delete Cloudinary image (id={}): {}", img.getImageId(), e.getMessage());
                    }
                    // Quan trọng: dùng helper để xóa khỏi collection và xóa back-reference
                    product.removeImage(img);
                }
                log.info("Marked {} images for removal via orphanRemoval for product id={}", imagesToRemove.size(), product.getId());
            } else {
                log.warn("None of the provided imageIdsToDelete found associated with product id={}", product.getId());
            }
        }
    }

    /** Helper: Upload và tạo entity Image (chưa save) */
    private Image uploadAndCreateImageEntity(MultipartFile imageFile) throws IOException {
        log.debug("Uploading image: {}", imageFile.getOriginalFilename());
        Map uploadResult = cloudinaryService.upload(imageFile);
        String imageUrl = (String) uploadResult.get("secure_url");
        String imageId = (String) uploadResult.get("public_id");
        log.debug("Upload complete. ImageUrl: {}, ImageId: {}", imageUrl, imageId);
        return Image.builder()
                .name(StringUtils.cleanPath(Objects.requireNonNull(imageFile.getOriginalFilename())))
                .imageUrl(imageUrl)
                .imageId(imageId)
                .build();
    }
}