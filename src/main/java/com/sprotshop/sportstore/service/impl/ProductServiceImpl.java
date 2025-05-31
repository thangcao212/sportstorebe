package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Image;
import com.sprotshop.sportstore.entity.Product;
import com.sprotshop.sportstore.entity.ProductCategory;
import com.sprotshop.sportstore.entity.ProductSize; // Import ProductSize
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.ImageRepository;
import com.sprotshop.sportstore.repository.ProductCategoryRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
// Import ProductSizeRepository if you create one and need specific methods,
// otherwise, JpaRepository<ProductSize, Long> can be used if needed directly.
// For now, ProductSize is managed via cascade from Product.
import com.sprotshop.sportstore.request.ProductRequest;
import com.sprotshop.sportstore.request.ProductSearchRequest;
import com.sprotshop.sportstore.response.ProductResponse;
import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.ProductCategoryService;
import com.sprotshop.sportstore.service.ProductService;
import com.sprotshop.sportstore.utils.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository; // Keep if still used directly, otherwise manage Images via Product
    private final ProductCategoryService productCategoryService;
    private final CloudinaryService cloudinaryService;
    private final ProductCategoryRepository productCategoryRepository; // Keep for category-specific logic like finding descendants

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) throws IOException {
        log.info("Creating product: {}", productRequest.getName());

        ProductCategory category = resolveCategoryFromRequest(productRequest);
        if (category == null) {
            throw new IllegalArgumentException("Category information is required and could not be resolved.");
        }
        log.info("Resolved category for new product: id={}, name='{}'", category.getId(), category.getName());

        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                // stockQuantity will be set based on sizes
                .productCategory(category)
                .images(new HashSet<>())
                .productSizes(new java.util.HashSet<>()) // Initialize productSizes set
                .build();

        // Process and add images
        processImageUploads(productRequest.getImages(), product);

        // Process and add product sizes, and calculate total stock
        int totalStock = 0;
        if (!CollectionUtils.isEmpty(productRequest.getSizes())) {
            log.info("Processing {} sizes for new product {}", productRequest.getSizes().size(), productRequest.getName());
            for (ProductRequest.ProductSizeRequest sizeRequest : productRequest.getSizes()) {
                ProductSize productSize = ProductSize.builder()
                        .size(sizeRequest.getSize())
                        .stockQuantity(sizeRequest.getStockQuantity())
                        // .product(product) // Will be set by product.addProductSize()
                        .build();
                product.addProductSize(productSize); // This sets the bidirectional link
                totalStock += sizeRequest.getStockQuantity();
                log.debug("Added size: {}, stock: {} to product {}", sizeRequest.getSize(), sizeRequest.getStockQuantity(), product.getName());
            }
        } else {
            // If no sizes are provided, use the stockQuantity from the request as total stock.
            // Or, you could enforce that sizes must be provided.
            log.warn("No sizes provided for product {}. Using stockQuantity from request if available, or defaulting to 0.", productRequest.getName());
            totalStock = productRequest.getStockQuantity() != null ? productRequest.getStockQuantity() : 0;
        }
        product.setStockQuantity(totalStock);
        log.info("Total calculated stock for product {}: {}", product.getName(), totalStock);


        Product savedProduct = productRepository.save(product); // Save product with images and sizes (cascaded)
        log.info("Product saved initially, id={}", savedProduct.getId());



        Product fullyLoadedProduct = findProductEntityById(savedProduct.getId()); // This will ensure all defined eager/EntityGraph paths are loaded
        log.info("Product fully reloaded after save, id={}", fullyLoadedProduct.getId());

        return ProductResponse.fromEntity(fullyLoadedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest productRequest) throws IOException {
        log.info("Updating product id: {}", id);
        // findProductEntityById uses @EntityGraph, so product.getImages() and product.getProductSizes() (if configured in graph) should be loaded.
        Product product = findProductEntityById(id);

        product.setName(productRequest.getName());
        product.setDescription(productRequest.getDescription());
        product.setPrice(productRequest.getPrice());
        log.debug("Basic info updated for product id: {}", id);

        updateProductCategoryIfNeeded(product, productRequest);
        processImageDeletions(productRequest.getImageIdsToDelete(), product); // Handles existing images
        processImageUploads(productRequest.getImages(), product); // Handles new images

        // Update product sizes
        // Clear existing sizes (orphanRemoval=true will delete them from DB)
        // and add new ones from the request.
        // This is a "replace all" strategy for sizes.
        if (productRequest.getSizes() != null) { // If null, don't touch sizes. If empty list, remove all.
            log.info("Updating sizes for product id: {}. Clearing existing {} sizes.", id, product.getProductSizes().size());
            // Need to iterate and remove to ensure orphanRemoval is triggered correctly by JPA
            // if just product.getProductSizes().clear() is not enough with some JPA providers/configs.
            // However, with orphanRemoval=true, product.getProductSizes().clear() followed by adding new ones
            // and then saving the product *should* work.
            List<ProductSize> oldSizes = new ArrayList<>(product.getProductSizes());
            for(ProductSize oldSize : oldSizes){
                product.removeProductSize(oldSize); // Ensure bidirectional link is broken
            }
            // product.getProductSizes().clear(); // Simpler, relies on orphanRemoval


            int totalStock = 0;
            if (!CollectionUtils.isEmpty(productRequest.getSizes())) {
                log.info("Adding/Updating with {} new sizes for product id: {}", productRequest.getSizes().size(), id);
                for (ProductRequest.ProductSizeRequest sizeRequest : productRequest.getSizes()) {
                    ProductSize productSize = ProductSize.builder()
                            .size(sizeRequest.getSize())
                            .stockQuantity(sizeRequest.getStockQuantity())
                            // .product(product) // Set by addProductSize
                            .build();
                    product.addProductSize(productSize);
                    totalStock += sizeRequest.getStockQuantity();
                    log.debug("Added/Updated size: {}, stock: {} for product id {}", sizeRequest.getSize(), sizeRequest.getStockQuantity(), id);
                }
            } else {
                log.info("Request contains an empty list of sizes for product id: {}. All existing sizes will be removed.", id);
                // totalStock remains 0 if no new sizes are added
            }
            product.setStockQuantity(totalStock);
            log.info("Total recalculated stock for product id {}: {}", id, totalStock);
        } else {
            // If productRequest.getSizes() is null, it means sizes are not part of this update.
            // The product's existing sizes and total stock quantity remain unchanged by this section.
            // If the productRequest.stockQuantity field is intended to update total stock independently,
            // that logic would go here, but it can conflict with size-based stock.
            // For now, if sizes aren't in request, Product.stockQuantity isn't changed by this block.
            log.debug("No size information in update request for product id: {}. Sizes and total stock (from sizes) remain unchanged.", id);
            // Optionally, if productRequest.getStockQuantity() should override:
            // product.setStockQuantity(productRequest.getStockQuantity());
            // But this is usually not desired if sizes dictate stock.
        }


        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully: id={}", updatedProduct.getId());
        // Reload to ensure response DTO gets fresh state, including any cascaded children.
        Product fullyLoadedProduct = findProductEntityById(updatedProduct.getId());
        return ProductResponse.fromEntity(fullyLoadedProduct);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) throws IOException {
        log.warn("Deleting product id: {}", id);
        Product product = findProductEntityById(id); // Load product, including images (due to EntityGraph)

        // Images are handled by cascade delete on product and orphanRemoval on product.images.
        // Cloudinary deletion needs to be explicit.
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            log.info("Deleting {} images from Cloudinary for product id: {}", product.getImages().size(), id);
            List<String> imageIds = product.getImages().stream()
                    .map(Image::getImageId).filter(Objects::nonNull).toList();
            for (String imageId : imageIds) {
                try { cloudinaryService.delete(imageId); }
                catch (IOException e) { log.error("Failed to delete Cloudinary image (id={}): {}", imageId, e.getMessage()); }
            }
        }
        // ProductSizes will be deleted by cascade due to Product deletion.

        productRepository.delete(product);
        log.warn("Product deleted successfully from DB: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.debug("Fetching product by id: {}", id);
        Product product = findProductEntityById(id); // Uses EntityGraph
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProducts() {
        log.debug("Fetching all products");
        // Ensure findAll() in repository uses @EntityGraph that includes productSizes if they should always be returned
        return ProductResponse.fromEntities(productRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsPage(Pageable pageable) {
        log.debug("Fetching products page: {}", pageable);
        // Ensure findAll(Pageable) in repository uses @EntityGraph for productSizes
        return productRepository.findAll(pageable).map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProductsByName(String name) {
        log.debug("Searching products by name containing: '{}'", name);
        // Ensure findByNameContainingIgnoreCase in repository uses @EntityGraph for productSizes
        return ProductResponse.fromEntities(productRepository.findByNameContainingIgnoreCase(name));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products by category id: {}", categoryId);
        ProductCategory category = productCategoryService.findCategoryEntityById(categoryId);
        // Ensure findByProductCategory in repository uses @EntityGraph for productSizes
        return ProductResponse.fromEntities(productRepository.findByProductCategory(category));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> searchProducts(ProductSearchRequest searchRequest, Pageable pageable) {
        log.debug("Searching products with criteria: {}, pageable: {}", searchRequest, pageable);
        if (searchRequest == null) {
            log.warn("Search request is null, returning empty page");
            return Page.empty(pageable);
        }

        Specification<Product> spec = Specification.where(null);

        if (StringUtils.hasText(searchRequest.getSearchValue())) {
            spec = spec.and(ProductSpecification.bySearchValue(searchRequest.getSearchValue()));
        }
        if (searchRequest.getCategoryId() != null) {
            List<Long> descendantIds = productCategoryRepository.findAllDescendantIds(searchRequest.getCategoryId());
            spec = spec.and(ProductSpecification.byCategoryId(searchRequest.getCategoryId(), descendantIds));
        }
        if (searchRequest.getMinPrice() != null || searchRequest.getMaxPrice() != null) {
            spec = spec.and(ProductSpecification.byPriceRange(searchRequest.getMinPrice(), searchRequest.getMaxPrice()));
        }
        // The byStockQuantity spec filters on Product.stockQuantity, which is now the total stock.
        if (searchRequest.getMinStock() != null || searchRequest.getMaxStock() != null) {
            spec = spec.and(ProductSpecification.byStockQuantity(searchRequest.getMinStock(), searchRequest.getMaxStock()));
        }

        // Ensure findAll(Specification, Pageable) in repository uses @EntityGraph for productSizes
        Page<Product> products = productRepository.findAll(spec, pageable);
        log.info("Found {} products on page {} of size {} matching search criteria",
                products.getTotalElements(), products.getNumber(), products.getSize());
        return products.map(ProductResponse::fromEntity);
    }

    // --- Helper Methods ---

    /** Tìm Product kèm Category, Images (và ProductSizes if included in EntityGraph) */
    private Product findProductEntityById(Long id){
        // Ensure your findById in ProductRepository has an @EntityGraph that includes "productSizes"
        // Example: @EntityGraph(attributePaths = {"productCategory", "images", "productSizes"})
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found with ID: " + id));
    }

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
        }
    }

    private void updateProductCategoryIfNeeded(Product product, ProductRequest request) {
        if (request.getCategoryId() != null || !CollectionUtils.isEmpty(request.getCategoryPath()) || StringUtils.hasText(request.getCategoryName())) {
            ProductCategory newCategory = resolveCategoryFromRequest(request);
            ProductCategory currentCategory = product.getProductCategory();
            if (currentCategory == null || !Objects.equals(currentCategory.getId(), newCategory.getId())) {
                product.setProductCategory(newCategory);
                log.info("Updated category for product id={} to category id={}", product.getId(), newCategory.getId());
            }
        }
    }

    private void processImageUploads(List<MultipartFile> imageFiles, Product product) throws IOException {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            log.info("Processing {} new image uploads for product id={}", imageFiles.size(), product.getId() != null ? product.getId() : "NEW");
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    Image image = uploadAndCreateImageEntity(file);
                    product.addImage(image); // Sets bidirectional link
                }
            }
        }
    }

    private void processImageDeletions(List<String> imageIdsToDelete, Product product) {
        if (imageIdsToDelete != null && !imageIdsToDelete.isEmpty()) {
            log.info("Processing {} image deletions for product id={}", imageIdsToDelete.size(), product.getId());
            List<Image> imagesToRemove = new ArrayList<>();
            // Ensure product.getImages() is initialized (it should be if findProductEntityById loads it)
            if(product.getImages() != null && Hibernate.isInitialized(product.getImages())) {
                product.getImages().stream()
                        .filter(img -> imageIdsToDelete.contains(img.getImageId()))
                        .forEach(imagesToRemove::add);
            } else {
                log.warn("Images collection not initialized for product id: {}. Cannot process deletions by Cloudinary ID.", product.getId());
                // Optionally, you could fetch images separately here if they weren't loaded,
                // but it's better to ensure they are loaded by findProductEntityById.
                return;
            }


            if (!imagesToRemove.isEmpty()) {
                log.debug("Found {} images associated with product to delete.", imagesToRemove.size());
                for (Image img : imagesToRemove) {
                    log.debug("Deleting image: dbId={}, cloudinaryId={}", img.getId(), img.getImageId());
                    try {
                        cloudinaryService.delete(img.getImageId());
                    } catch (IOException e) {
                        log.error("Failed to delete Cloudinary image (id={}): {}", img.getImageId(), e.getMessage());
                    }
                    product.removeImage(img); // Important for orphanRemoval and breaking link
                }
                log.info("Marked {} images for removal via orphanRemoval for product id={}", imagesToRemove.size(), product.getId());
            } else {
                log.warn("None of the provided imageIdsToDelete found associated with product id={}", product.getId());
            }
        }
    }

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