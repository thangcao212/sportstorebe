package com.sprotshop.sportstore.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprotshop.sportstore.entity.*;
import com.sprotshop.sportstore.exception.NotFoundException;
import com.sprotshop.sportstore.repository.BrandRepository;
import com.sprotshop.sportstore.repository.ImageRepository;
import com.sprotshop.sportstore.repository.ProductCategoryRepository;
import com.sprotshop.sportstore.repository.ProductRepository;
import com.sprotshop.sportstore.request.BrandRequest;
import com.sprotshop.sportstore.request.ProductRequest;
import com.sprotshop.sportstore.request.ProductSearchRequest;
import com.sprotshop.sportstore.response.BrandResponse;
import com.sprotshop.sportstore.response.ProductResponse;
import com.sprotshop.sportstore.service.BrandService;
import com.sprotshop.sportstore.service.CloudinaryService;
import com.sprotshop.sportstore.service.ProductCategoryService;
import com.sprotshop.sportstore.service.ProductService;
import com.sprotshop.sportstore.utils.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final ProductCategoryService productCategoryService;
    private final CloudinaryService cloudinaryService;
    private final ProductCategoryRepository productCategoryRepository;
    private final BrandService brandService; //
    private final BrandRepository brandRepository;
    private final ObjectMapper objectMapper; // For any JSON parsing if needed

    @Override
    @Transactional
    public ProductResponse createProduct(ProductRequest productRequest) throws IOException {
        log.info("Creating product: {}", productRequest.getName());

        ProductCategory category = resolveCategoryFromRequest(productRequest);
        if (category == null) {
            throw new IllegalArgumentException("Category information is required and could not be resolved.");
        }
        log.info("Resolved category for new product: id={}, name='{}'", category.getId(), category.getName());

        // 👈 Added: Resolve Brand
        Brand brand = null;
        if (productRequest.getBrandId() != null) {
            brand = brandRepository.findById(productRequest.getBrandId())
                    .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + productRequest.getBrandId()));
            log.info("Resolved brand for new product: id={}, name='{}'", brand.getId(), brand.getName());
        } else {
            log.warn("No brand ID provided for new product {}. Brand will be null.", productRequest.getName());
        }

        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice()) // Convert Double to BigDecimal
                // stockQuantity will be set based on sizes
                .productCategory(category)
                .brand(brand) // 👈 Set Brand
                .images(new HashSet<>())
                .productSizes(new HashSet<>()) // Initialize productSizes set
                .build();

        // Process and add images - handles both files and URLs
        processImages(productRequest, product);

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

        // 👈 Added: Update Brand
        if (productRequest.getBrandId() != null) {
            Brand brand = brandRepository.findById(productRequest.getBrandId())
                    .orElseThrow(() -> new NotFoundException("Brand not found with ID: " + productRequest.getBrandId()));
            product.setBrand(brand);
            log.info("Updated brand for product id={} to brand id={}", id, brand.getId());
        }

        updateProductCategoryIfNeeded(product, productRequest);
        processImageDeletions(productRequest.getImageIdsToDelete(), product); // Handles existing images
        processImages(productRequest, product); // Handles new images (files or URLs, but for update, assume files)

        if (productRequest.getSizes() != null) { // If null, don't touch sizes. If empty list, remove all.
            log.info("Updating sizes for product id: {}. Clearing existing {} sizes.", id, product.getProductSizes().size());

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
            log.debug("No size information in update request for product id: {}. Sizes and total stock (from sizes) remain unchanged.", id);

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
        Product product = findProductEntityById(id);

        if (product.getImages() != null && !product.getImages().isEmpty()) {
            log.info("Deleting {} images from Cloudinary for product id: {}", product.getImages().size(), id);
            List<String> imageIds = product.getImages().stream()
                    .map(Image::getImageId).filter(Objects::nonNull).toList();
            for (String imageId : imageIds) {
                try {
                    cloudinaryService.delete(imageId);
                } catch (IOException e) {
                    log.error("Failed to delete Cloudinary image (id={}): {}", imageId, e.getMessage());
                }
            }
        }

        productRepository.delete(product);
        log.warn("Product deleted successfully from DB: id={}", id);
    }

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
        return ProductResponse.fromEntities(productRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getProductsPage(Pageable pageable) {
        log.debug("Fetching products page: {}", pageable);
        return productRepository.findAll(pageable).map(ProductResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> searchProductsByName(String name) {
        log.debug("Searching products by name containing: '{}'", name);
        return ProductResponse.fromEntities(productRepository.findByNameContainingIgnoreCase(name));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCategory(Long categoryId) {
        log.debug("Fetching products by category id: {}", categoryId);
        ProductCategory category = productCategoryService.findCategoryEntityById(categoryId);
        return ProductResponse.fromEntities(productRepository.findByProductCategory(category));
    }

    // Trong ProductServiceImpl.java - method searchProducts (không thay đổi, vẫn gọi fetchBrand())
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
        if (searchRequest.getMinStock() != null || searchRequest.getMaxStock() != null) {
            spec = spec.and(ProductSpecification.byStockQuantity(searchRequest.getMinStock(), searchRequest.getMaxStock()));
        }

        // 👈 Added: Lọc theo brand ID
        if (searchRequest.getBrandId() != null) {
            spec = spec.and(ProductSpecification.byBrandId(searchRequest.getBrandId()));
        }

        // 👈 THÊM: Fetch join brand để load eager (đã handle count query trong spec)
        spec = spec.and(ProductSpecification.fetchBrand());

        // Optional: Nếu cần sizes/images không empty trong response
        // spec = spec.and(ProductSpecification.fetchSizesAndImages());

        Page<Product> products = productRepository.findAll(spec, pageable);
        log.info("Found {} products on page {} of size {} matching search criteria",
                products.getTotalElements(), products.getNumber(), products.getSize());
        return products.map(ProductResponse::fromEntity);
    }
    @Override
    @Transactional
    public List<ProductResponse> importProductsFromExcel(MultipartFile excelFile) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("Starting Excel import for products: filename={}", excelFile.getOriginalFilename());
        Map<String, ProductCategory> categoryCache = new HashMap<>();
        Map<String, Brand> brandCache = new HashMap<>(); // 👈 Added: Brand cache
        List<Product> productsToBatchSave = new ArrayList<>();
        List<ProductResponse> importedProducts = new ArrayList<>();
        try (InputStream is = excelFile.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheet("Products");
            if (sheet == null) {
                throw new IllegalArgumentException("Excel must have a sheet named 'Products'");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ProductRequest productRequest = parseRowToProductRequest(row);
                    if (productRequest.getName() == null || productRequest.getName().trim().isEmpty()) {
                        log.warn("Skipping empty row {}", i);
                        continue;
                    }

                    // Cache category
                    String catName = productRequest.getCategoryName();
                    String catKey = catName.toLowerCase();
                    ProductCategory category = categoryCache.computeIfAbsent(catKey, k ->
                            productCategoryService.findOrCreateCategory(k, null, null));
                    productRequest.setCategoryId(category.getId());

                    // 👈 Added: Cache brand (find or create if not exist)
                    String brandName = productRequest.getBrandName(); // Assume parsed from row
                    if (StringUtils.hasText(brandName)) {
                        String brandKey = brandName.toLowerCase();
                        Brand brand = brandCache.computeIfAbsent(brandKey, k -> {
                            // Find existing or create new
                            Optional<Brand> existingBrand = brandRepository.findByNameIgnoreCase(brandName);
                            if (existingBrand.isPresent()) {
                                log.info("Using existing brand: {}", brandName);
                                return existingBrand.get();
                            } else {
                                // Create new (description and logoUrl can be null or from row if added)
                                BrandRequest brandRequest = BrandRequest.builder()
                                        .name(brandName)
                                        .description(null) // Or parse from another column
                                        .logoUrl(null) // Or parse from another column
                                        .build();
                                try {
                                    // 👈 Fix: Call createBrand to get BrandResponse, then fetch entity
                                    BrandResponse brandResp = brandService.createBrand(brandRequest);
                                    log.info("Created new brand: {}", brandName);
                                    // Fetch the entity from repo to return Brand (not Response)
                                    return brandRepository.findById(brandResp.getId())
                                            .orElseThrow(() -> new RuntimeException("Failed to fetch new brand: " + brandName));
                                } catch (Exception e) {
                                    log.error("Failed to create brand {}: {}", brandName, e.getMessage());
                                    return null;
                                }
                            }
                        });
                        if (brand != null) {
                            productRequest.setBrandId(brand.getId());
                        }
                    } else {
                        log.warn("No brand name provided for row {}", i);
                    }

                    // Tạo product với images từ URLs (parallel direct upload)
                    Product product = createProductFromRequest(productRequest, category);
                    productsToBatchSave.add(product);

                    log.info("Parsed product: {} (row {})", productRequest.getName(), i);
                } catch (Exception e) {
                    log.error("Error importing row {}: {}", i, e.getMessage(), e);
                }
            }

            // Batch save all
            List<Product> savedProducts = productRepository.saveAll(productsToBatchSave);
            log.info("Batch saved {} products", savedProducts.size());

            // Reload for response
            importedProducts = savedProducts.stream()
                    .map(saved -> ProductResponse.fromEntity(findProductEntityById(saved.getId())))
                    .collect(Collectors.toList());
        }
        long endTime = System.currentTimeMillis();
        log.info("Excel import completed in {} ms. Imported {} products.", endTime - startTime, importedProducts.size());
        return importedProducts;
    }
    // Helper: Tạo product từ request (gọi processImageUploads + sizes) - for Excel, uses URLs
    private Product createProductFromRequest(ProductRequest request, ProductCategory category) throws IOException {
        // 👈 Added: Resolve Brand in createFromRequest
        Brand brand = null;
        if (request.getBrandId() != null) {
            brand = brandRepository.findById(request.getBrandId()).orElse(null);
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .productCategory(category)
                .brand(brand)
                .images(new HashSet<>())
                .productSizes(new HashSet<>())
                .build();

        // For Excel: process URLs
        processImageUrls(request.getImageUrls(), product);

        // Add sizes
        int totalStock = 0;
        if (!CollectionUtils.isEmpty(request.getSizes())) {
            for (ProductRequest.ProductSizeRequest sizeRequest : request.getSizes()) {
                ProductSize productSize = ProductSize.builder()
                        .size(sizeRequest.getSize())
                        .stockQuantity(sizeRequest.getStockQuantity())
                        .build();
                product.addProductSize(productSize);
                totalStock += sizeRequest.getStockQuantity();
            }
        } else {
            totalStock = request.getStockQuantity() != null ? request.getStockQuantity() : 0;
        }
        product.setStockQuantity(totalStock);

        return product;
    }

    // Method to process images - unified for create (handles files or URLs)
    private void processImages(ProductRequest request, Product product) throws IOException {
        // Priority: files for regular create/update
        if (!CollectionUtils.isEmpty(request.getImages())) {
            processFileUploads(request.getImages(), product);
        } else if (!CollectionUtils.isEmpty(request.getImageUrls())) {
            // Fallback to URLs (for Excel or if no files)
            processImageUrls(request.getImageUrls(), product);
        }
    }

    // Process MultipartFile uploads (old logic for create/update)
    private void processFileUploads(List<MultipartFile> imageFiles, Product product) throws IOException {
        if (imageFiles != null && !imageFiles.isEmpty()) {
            log.info("Processing {} new image uploads for product {}", imageFiles.size(), product.getName() != null ? product.getName() : "NEW");
            for (MultipartFile file : imageFiles) {
                if (file != null && !file.isEmpty()) {
                    Image image = uploadAndCreateImageEntity(file);
                    product.addImage(image); // Sets bidirectional link
                }
            }
        }
    }

    // Process URL uploads (for Excel import)
    private void processImageUrls(List<String> imageUrls, Product product) throws IOException {
        if (imageUrls != null && !imageUrls.isEmpty()) {
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<CompletableFuture<Image>> futures = imageUrls.stream()
                    .filter(StringUtils::hasText)
                    .map(url -> CompletableFuture.supplyAsync(() -> uploadDirectFromUrl(url), executor))
                    .collect(Collectors.toList());
            List<Image> images = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            for (Image image : images) {
                product.addImage(image);
            }
            executor.shutdown();
            log.info("Parallel direct uploaded {} images for product {}", images.size(), product.getName());
        }
    }

    private Image uploadDirectFromUrl(String url) {
        try {
            // Thử direct URL upload
            Map uploadResult = cloudinaryService.uploadUrl(url);
            if (uploadResult != null && uploadResult.get("secure_url") != null) {
                String imageUrl = (String) uploadResult.get("secure_url");
                String imageId = (String) uploadResult.get("public_id");
                log.debug("Direct upload success for {}", url);
                return Image.builder()
                        .name(url.substring(url.lastIndexOf('/') + 1))
                        .imageUrl(imageUrl)
                        .imageId(imageId)
                        .build();
            } else {
                log.warn("Direct upload failed for {}, fallback to download + upload", url);
                // Fallback: Download bytes
                MultipartFile tempFile = downloadSingleImage(url);
                if (tempFile != null && !tempFile.isEmpty()) {
                    Map fallbackResult = cloudinaryService.upload(tempFile);
                    String imageUrl = (String) fallbackResult.get("secure_url");
                    String imageId = (String) fallbackResult.get("public_id");
                    log.debug("Fallback upload success for {}", url);
                    return Image.builder()
                            .name(tempFile.getOriginalFilename())
                            .imageUrl(imageUrl)
                            .imageId(imageId)
                            .build();
                }
            }
        } catch (Exception e) {
            log.warn("Upload fail for {}: {}", url, e.getMessage());
        }
        return null;
    }

    private MultipartFile downloadSingleImage(String url) {
        try {
            log.debug("Fallback download: {}", url);
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                try (InputStream inputStream = connection.getInputStream();
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    byte[] imageBytes = outputStream.toByteArray();
                    if (imageBytes.length > 0) {
                        String filename = url.substring(url.lastIndexOf('/') + 1);
                        if (filename.contains("?")) filename = filename.split("\\?")[0];
                        if (!filename.contains(".")) filename += ".jpg";
                        return new ByteArrayMultipartFile(imageBytes, filename, "image/jpeg");
                    }
                }
            } else {
                log.warn("Fallback HTTP fail: {} - Code {}", url, responseCode);
            }
        } catch (Exception e) {
            log.warn("Fallback download fail for {}: {}", url, e.getMessage());
        }
        return null;
    }

    // ByteArrayMultipartFile class for fallback
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;
        private final String contentType;

        public ByteArrayMultipartFile(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() { return filename; }

        @Override
        public String getOriginalFilename() { return filename; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return content == null || content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() throws IOException { return content; }

        @Override
        public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException { Files.write(dest.toPath(), content); }
    }

    // Method parseRowToProductRequest (from initial for Excel)
    private ProductRequest parseRowToProductRequest(Row row) {
        String name = getCellValueAsString(row.getCell(0));
        String description = getCellValueAsString(row.getCell(1));
        BigDecimal priceBd = getCellValueAsBigDecimal(row.getCell(2));
        String categoryName = getCellValueAsString(row.getCell(3));
        String sizesStr = getCellValueAsString(row.getCell(4));
        String stockQuantitiesStr = getCellValueAsString(row.getCell(5));

        // Parse imageUrls (cột G)
        String imageUrlsStr = getCellValueAsString(row.getCell(6));
        List<String> imageUrls = StringUtils.hasText(imageUrlsStr)
                ? Arrays.stream(imageUrlsStr.split(","))
                .map(String::trim)
                .collect(Collectors.toList())
                : new ArrayList<>();

        ProductRequest request = ProductRequest.builder()
                .name(name)
                .description(description)
                .price(priceBd != null ? priceBd : null)
                .categoryName(categoryName)
                .imageUrls(imageUrls)  // Set URLs for Excel
                .build();

        int totalStock = 0;
        if (StringUtils.hasText(sizesStr) && StringUtils.hasText(stockQuantitiesStr)) {
            String[] sizes = sizesStr.split(",");
            String[] quantities = stockQuantitiesStr.split(",");
            if (sizes.length == quantities.length) {
                List<ProductRequest.ProductSizeRequest> sizeRequests = new ArrayList<>();
                for (int j = 0; j < sizes.length; j++) {
                    try {
                        ProductRequest.ProductSizeRequest sizeReq = ProductRequest.ProductSizeRequest.builder()
                                .size(sizes[j].trim().toUpperCase())
                                .stockQuantity(Integer.parseInt(quantities[j].trim()))
                                .build();
                        sizeRequests.add(sizeReq);
                        totalStock += Integer.parseInt(quantities[j].trim());
                    } catch (NumberFormatException e) {
                        log.warn("Invalid stock quantity '{}' for size '{}'", quantities[j], sizes[j]);
                    }
                }
                request.setSizes(sizeRequests);
            } else {
                log.warn("Mismatched sizes and quantities in row: sizes={}, quantities={}", sizesStr, stockQuantitiesStr);
            }
        } else if (StringUtils.hasText(stockQuantitiesStr)) {
            try {
                totalStock = Integer.parseInt(stockQuantitiesStr.trim());
                request.setStockQuantity(totalStock);
            } catch (NumberFormatException e) {
                log.warn("Invalid total stock '{}'", stockQuantitiesStr);
            }
        }

        return request;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private BigDecimal getCellValueAsBigDecimal(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        return null;
    }

    private Product findProductEntityById(Long id) {
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

    private void processImageDeletions(List<String> imageIdsToDelete, Product product) {
        if (imageIdsToDelete != null && !imageIdsToDelete.isEmpty()) {
            log.info("Processing {} image deletions for product id={}", imageIdsToDelete.size(), product.getId());
            List<Image> imagesToRemove = new ArrayList<>();
            if (product.getImages() != null && Hibernate.isInitialized(product.getImages())) {
                product.getImages().stream()
                        .filter(img -> imageIdsToDelete.contains(img.getImageId()))
                        .forEach(imagesToRemove::add);
            } else {
                log.warn("Images collection not initialized for product id: {}. Cannot process deletions by Cloudinary ID.", product.getId());
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
                    product.removeImage(img);
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