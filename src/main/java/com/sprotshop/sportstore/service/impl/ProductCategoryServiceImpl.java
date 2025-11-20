package com.sprotshop.sportstore.service.impl; // Thay đổi package nếu cần

import com.sprotshop.sportstore.entity.ProductCategory;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.ProductCategoryRepository;
import com.sprotshop.sportstore.request.ProductCategoryHierarchyRequest;
import com.sprotshop.sportstore.request.ProductCategoryRequest;
import com.sprotshop.sportstore.response.ProductCategoryResponse;
import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.ProductCategoryService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate; // Import Hibernate
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils; // Import CollectionUtils
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Class triển khai các dịch vụ quản lý Danh mục sản phẩm.
 */
@Service
@RequiredArgsConstructor // Đảm bảo productCategoryRepository được inject
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private static final Logger log = LoggerFactory.getLogger(ProductCategoryServiceImpl.class);
    private final ProductCategoryRepository productCategoryRepository; // Chỉ cần inject repo này
    private final CloudinaryService cloudinaryService;

    // --- Implementation các phương thức chính từ Interface ---

    @Override
    @Transactional
    public ProductCategoryResponse createCategory(ProductCategoryRequest categoryRequest) throws IOException {
        log.info("Creating category: name='{}', parentId={}", categoryRequest.getName(), categoryRequest.getParentId());
        String normalizedName = categoryRequest.getName().toLowerCase();
        // Sử dụng hàm helper nội bộ để tìm parent entity
        ProductCategory parent = findParentEntityInternal(categoryRequest.getParentId());
        // Sử dụng hàm helper nội bộ để kiểm tra trùng lặp
        checkDuplicateNameInternal(normalizedName, categoryRequest.getParentId(), null);

        ProductCategory category = ProductCategory.builder()
                .name(normalizedName)
                .description(categoryRequest.getDescription())
                .parent(parent)
                .build();

        // Process image upload if provided
        processImageUpload(categoryRequest.getImage(), category, true);

        ProductCategory savedCategory = productCategoryRepository.save(category);
        log.info("Category created successfully: id={}", savedCategory.getId());
        // 👈 Sửa: Tính subtree product count cho response
        return buildCategoryResponse(savedCategory);
    }

    @Override
    @Transactional
    public ProductCategoryResponse updateCategory(Long id, ProductCategoryRequest categoryRequest) throws IOException {
        log.info("Updating category: id={}", id);
        // Sử dụng hàm helper nội bộ để tìm entity
        ProductCategory category = findCategoryEntityByIdInternal(id);
        String normalizedName = categoryRequest.getName().toLowerCase();
        Long newParentId = categoryRequest.getParentId();
        Long currentParentId = (category.getParent() != null) ? category.getParent().getId() : null;

        if (!normalizedName.equals(category.getName()) || !Objects.equals(newParentId, currentParentId)) {
            // Sử dụng hàm helper nội bộ để kiểm tra trùng lặp
            checkDuplicateNameInternal(normalizedName, newParentId, id);
        }

        category.setName(normalizedName);
        category.setDescription(categoryRequest.getDescription());
        // Sử dụng hàm helper nội bộ để cập nhật parent và kiểm tra vòng lặp
        updateParentInternal(category, newParentId);

        // Handle image update: delete old if specified, upload new if provided
        if (StringUtils.hasText(categoryRequest.getImageIdToDelete()) &&
                Objects.equals(categoryRequest.getImageIdToDelete(), category.getImageId())) {
            deleteImageFromCloudinary(category.getImageId());
            category.setImageUrl(null);
            category.setImageId(null);
            log.info("Old image deleted for category id: {}", id);
        }
        processImageUpload(categoryRequest.getImage(), category, false);

        ProductCategory updatedCategory = productCategoryRepository.save(category);
        log.info("Category updated successfully: id={}", updatedCategory.getId());
        // 👈 Sửa: Tính subtree product count cho response
        return buildCategoryResponse(updatedCategory);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) throws IOException {
        log.warn("Deleting category: id={}", id); // Log WARN cho hành động xóa
        ProductCategory category = findCategoryEntityByIdInternal(id);

        // Kiểm tra product collection trước khi xóa
        if (Hibernate.isInitialized(category.getProducts()) && !category.getProducts().isEmpty()) {
            log.error("Cannot delete category id={} because it contains products.", id);
            throw new IllegalStateException("Cannot delete category id=" + id + " as it contains products.");
        } else if (!Hibernate.isInitialized(category.getProducts())) {
            log.warn("Product collection for category id={} was not initialized. Assuming no products for deletion check.", id);
            // 👈 Sửa: Sử dụng query để kiểm tra chính xác
            long subtreeProductCount = productCategoryRepository.countProductsInSubtree(id);
            if (subtreeProductCount > 0) {
                log.error("Cannot delete category id={} because subtree contains {} products.", id, subtreeProductCount);
                throw new IllegalStateException("Cannot delete category id=" + id + " as its subtree contains " + subtreeProductCount + " products.");
            }
        }

        // Delete image from Cloudinary if exists
        if (StringUtils.hasText(category.getImageId())) {
            deleteImageFromCloudinary(category.getImageId());
            log.info("Image deleted from Cloudinary for category id: {}", id);
        }

        // Xử lý children
        // Load children nếu chưa load để xử lý
        if (!Hibernate.isInitialized(category.getChildren())) {
            log.debug("Initializing children collection for category id={} before processing deletion.", id);
            Hibernate.initialize(category.getChildren());
        }

        if (!category.getChildren().isEmpty()) {
            log.warn("Category id={} has children. Moving them to parent id={}", id, category.getParent() != null ? category.getParent().getId() : "null");
            ProductCategory parentOfDeleted = category.getParent();
            List<ProductCategory> childrenToMove = List.copyOf(category.getChildren()); // Tạo bản sao an toàn
            for (ProductCategory child : childrenToMove) {
                child.setParent(parentOfDeleted);
                productCategoryRepository.save(child); // Lưu lại từng child với parent mới
            }
            category.getChildren().clear(); // Quan trọng: Xóa association khỏi collection của parent sắp bị xóa
            log.debug("Cleared children association from category id={}", id);
        }

        productCategoryRepository.delete(category);
        log.warn("Category deleted successfully from DB: id={}", id); // Log WARN khi xóa thành công
    }

    @Override
    @Transactional(readOnly = true)
    public ProductCategoryResponse getCategoryById(Long id) {
        log.debug("Fetching category by id: {}", id);
        ProductCategory category = findCategoryEntityByIdInternal(id);
        // 👈 Sửa: Tính subtree product count cho response
        return buildCategoryResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getRootCategories() {
        log.debug("Fetching root categories");
        List<ProductCategory> roots = productCategoryRepository.findByParentIsNull();
        // 👈 Sửa: Tính subtree cho từng root
        return roots.stream()
                .map(this::buildCategoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getChildCategories(Long parentId) {
        log.debug("Fetching children for parentId: {}", parentId);
        ProductCategory parent = findCategoryEntityByIdInternal(parentId);
        List<ProductCategory> children = productCategoryRepository.findByParent(parent);
        // 👈 Sửa: Tính subtree cho từng child
        return children.stream()
                .map(this::buildCategoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductCategoryResponse createCategoryHierarchy(ProductCategoryHierarchyRequest request) {
        log.info("Creating category hierarchy via dedicated endpoint: {}", request.getCategoryNames());
        // Gọi hàm helper nội bộ trả về entity lá
        ProductCategory leafCategory = findOrCreateCategoryHierarchyInternal(request.getCategoryNames());
        log.info("Hierarchy creation completed via endpoint. Leaf category id={}", leafCategory.getId());
        // 👈 Sửa: Tính subtree product count cho response (leaf)
        return buildCategoryResponse(leafCategory);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductCategoryResponse> getCategoriesPage(Pageable pageable) {
        log.debug("Fetching categories page: {}", pageable);
        Page<ProductCategory> categoryPage = productCategoryRepository.findAll(pageable);
        // 👈 Sửa: Tính subtree cho từng category trong page
        return categoryPage.map(this::buildCategoryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> getAllCategories() {
        log.debug("Fetching all categories");
        List<ProductCategory> categories = productCategoryRepository.findAll(); // Có thể thêm Sort.by("name")
        // 👈 Sửa: Tính subtree cho tất cả
        return categories.stream()
                .map(this::buildCategoryResponse)
                .collect(Collectors.toList());
    }

    // 👈 Thêm method helper mới để build Response với subtree product count
    private ProductCategoryResponse buildCategoryResponse(ProductCategory category) {
        ProductCategory parent = category.getParent();
        Long parentIdValue = (parent != null) ? parent.getId() : null;
        String parentNameValue = (parent != null) ? parent.getName() : null;

        List<Long> childIdsValue = (category.getChildren() != null) ?
                category.getChildren().stream().map(ProductCategory::getId).collect(Collectors.toList()) :
                Collections.emptyList();

        // 👈 Tính tổng sản phẩm trong subtree (category + tất cả con cái)
        long subtreeCount = (category.getId() != null) ? productCategoryRepository.countProductsInSubtree(category.getId()) : 0L;

        return ProductCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .imageId(category.getImageId())
                .parentId(parentIdValue)
                .parentName(parentNameValue)
                .childrenIds(childIdsValue)
                .productCount((int) subtreeCount) // Cast sang int, giả sử không quá 2^31
                .build();
    }

    // --- Implementation các hàm helper trả về Entity (để ProductServiceImpl sử dụng) ---
    // Các hàm này có thể là public nếu Interface yêu cầu, hoặc private nếu chỉ dùng nội bộ

    @Override // Đánh dấu @Override nếu nó override từ interface
    @Transactional(readOnly = true) // Chỉ đọc
    public ProductCategory findCategoryEntityById(Long id) throws NotFoundException {
        return findCategoryEntityByIdInternal(id); // Gọi hàm private
    }

    @Override // Đánh dấu @Override nếu nó override từ interface
    @Transactional // Cần Transaction vì có thể save
    public ProductCategory findOrCreateCategory(String normalizedName, String description, Long parentId) {
        return findOrCreateCategoryInternal(normalizedName, description, parentId); // Gọi hàm private
    }

    @Override // Đánh dấu @Override nếu nó override từ interface
    @Transactional // Cần Transaction vì có thể save nhiều lần
    public ProductCategory findOrCreateCategoryHierarchy(List<@NotBlank String> categoryNames) {
        return findOrCreateCategoryHierarchyInternal(categoryNames); // Gọi hàm private
    }


    // --- Các hàm Helper nội bộ (Private) ---

    private ProductCategory findCategoryEntityByIdInternal(Long id) {
        log.debug("Internal: Finding category entity with id: {}", id);
        return productCategoryRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Internal: Category not found with id: {}", id);
                    return new NotFoundException("Không tìm thấy danh mục với ID: " + id);
                });
    }

    private ProductCategory findParentEntityInternal(Long parentId) {
        if (parentId == null) {
            return null;
        }
        log.debug("Internal: Finding parent entity with id: {}", parentId);
        return findCategoryEntityByIdInternal(parentId); // Dùng lại hàm tìm entity
    }

    private void checkDuplicateNameInternal(String normalizedName, Long parentId, Long excludeId) {
        log.debug("Internal: Checking duplicate name: name='{}', parentId={}, excludeId={}", normalizedName, parentId, excludeId);
        Optional<ProductCategory> existing = productCategoryRepository.findByNameAndParentId(normalizedName, parentId);
        if (existing.isPresent() && (excludeId == null || !existing.get().getId().equals(excludeId))) {
            String parentInfo = (parentId == null) ? "cấp gốc" : "dưới danh mục cha ID " + parentId;
            String errorMessage = "Tên danh mục '" + normalizedName + "' đã tồn tại ở " + parentInfo + ".";
            log.warn("Internal: Duplicate name detected: {}", errorMessage);
            throw new DataIntegrityViolationException(errorMessage);
        }
        log.debug("Internal: No duplicate name found.");
    }

    private void updateParentInternal(ProductCategory category, Long newParentId) {
        Long currentParentId = (category.getParent() != null) ? category.getParent().getId() : null;
        if (!Objects.equals(newParentId, currentParentId)) {
            log.debug("Internal: Updating parent for category id={}: from {} to {}", category.getId(), currentParentId, newParentId);
            if (newParentId != null) {
                if (newParentId.equals(category.getId())) {
                    throw new IllegalArgumentException("Một danh mục không thể là cha của chính nó.");
                }
                ProductCategory newParent = findCategoryEntityByIdInternal(newParentId);
                checkCircularReferenceInternal(category, newParent);
                category.setParent(newParent);
                log.info("Internal: Set new parent (id={}) for category id={}", newParentId, category.getId());
            } else {
                category.setParent(null);
                log.info("Internal: Removed parent for category id={}", category.getId());
            }
        }
    }

    private void checkCircularReferenceInternal(ProductCategory categoryToCheck, ProductCategory potentialParent) {
        log.debug("Internal: Checking circular reference: categoryId={}, potentialParentId={}", categoryToCheck.getId(), potentialParent.getId());
        ProductCategory current = potentialParent;
        int depth = 0;
        while (current != null && depth < 100) { // Depth limit
            if (current.getId().equals(categoryToCheck.getId())) {
                log.error("Internal: Circular reference detected!");
                throw new IllegalArgumentException("Không thể tạo tham chiếu vòng trong cấu trúc danh mục.");
            }
            // Giả định getParent() đủ để load nếu cần (do entity được quản lý)
            current = current.getParent();
            depth++;
        }
        if (depth >= 100) log.warn("Internal: Circular reference check reached max depth (100) for categoryId={}", categoryToCheck.getId());
        log.debug("Internal: Circular reference check passed.");
    }

    // Hàm này là implement thực sự của findOrCreateCategory, dùng nội bộ hoặc qua interface
    private ProductCategory findOrCreateCategoryInternal(String normalizedName, String description, Long parentId) {
        log.debug("Internal: Finding or creating single category: name='{}', parentId={}", normalizedName, parentId);
        // Tên là bắt buộc để tạo mới
        if (!StringUtils.hasText(normalizedName)) {
            throw new IllegalArgumentException("Category name is required to find or create.");
        }
        Optional<ProductCategory> existing = productCategoryRepository.findByNameAndParentId(normalizedName, parentId);

        if (existing.isPresent()) {
            log.debug("Internal: Found existing single category: id={}", existing.get().getId());
            return existing.get();
        } else {
            log.debug("Internal: Creating new single category: name='{}', parentId={}", normalizedName, parentId);
            ProductCategory parent = findParentEntityInternal(parentId); // Tìm cha nếu có parentId
            ProductCategory newCategory = ProductCategory.builder()
                    .name(normalizedName)
                    .description(description)
                    .parent(parent)
                    .build();
            ProductCategory saved = productCategoryRepository.save(newCategory);
            log.info("Internal: Created new single category: id={}", saved.getId());
            return saved;
        }
    }

    // Hàm này là implement thực sự của findOrCreateCategoryHierarchy, dùng nội bộ hoặc qua interface
    private ProductCategory findOrCreateCategoryHierarchyInternal(List<@NotBlank String> categoryNames) {
        if (CollectionUtils.isEmpty(categoryNames)) {
            throw new IllegalArgumentException("Category names path cannot be null or empty.");
        }
        log.info("Internal: Finding or creating category hierarchy for path: {}", categoryNames);
        ProductCategory currentParent = null;
        ProductCategory lastProcessedCategory = null;
        int level = 0;

        for (String categoryName : categoryNames) {
            level++;
            String normalizedName = categoryName.toLowerCase();
            Long parentId = (currentParent != null) ? currentParent.getId() : null;
            log.debug("Internal: Processing hierarchy level {}: name='{}', parentId={}", level, normalizedName, parentId);

            Optional<ProductCategory> existingOpt = productCategoryRepository.findByNameAndParentId(normalizedName, parentId);

            if (existingOpt.isPresent()) {
                currentParent = existingOpt.get();
                log.debug("Internal: Found existing category in path: id={}, name='{}'", currentParent.getId(), normalizedName);
            } else {
                log.debug("Internal: Creating new category in path: name='{}', parentId={}", normalizedName, parentId);
                ProductCategory newCategory = ProductCategory.builder()
                        .name(normalizedName)
                        .parent(currentParent)
                        // Lấy description từ đâu? Có thể thêm vào request hoặc để null
                        .build();
                currentParent = productCategoryRepository.save(newCategory);
                log.info("Internal: Saved new category in path: id={}, name='{}'", currentParent.getId(), normalizedName);
            }
            lastProcessedCategory = currentParent;
        }
        if (lastProcessedCategory == null) {
            throw new IllegalStateException("Internal: Failed to process category hierarchy path.");
        }
        log.debug("Internal: Hierarchy processed. Leaf category: id={}", lastProcessedCategory.getId());
        return lastProcessedCategory;
    }

    // Helper: Process image upload (create or update)
    private void processImageUpload(MultipartFile imageFile, ProductCategory category, boolean isCreate) throws IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            log.info("Processing image upload for category: {}", category.getName() != null ? category.getName() : "NEW");
            // Delete old image if updating and new image provided
            if (!isCreate && StringUtils.hasText(category.getImageId())) {
                deleteImageFromCloudinary(category.getImageId());
                log.info("Old image replaced for category id: {}", category.getId());
            }
            // Upload new image
            Map uploadResult = cloudinaryService.upload(imageFile);
            String imageUrl = (String) uploadResult.get("secure_url");
            String imageId = (String) uploadResult.get("public_id");
            category.setImageUrl(imageUrl);
            category.setImageId(imageId);
            log.debug("Image uploaded: URL={}, ID={} for category", imageUrl, imageId);
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