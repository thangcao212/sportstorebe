package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.request.ProductRequest;
import com.sprotshop.sportstore.request.ProductSearchRequest;
import com.sprotshop.sportstore.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.util.List;

public interface ProductService {

    ProductResponse createProduct(ProductRequest productRequest) throws IOException;

    ProductResponse updateProduct(Long id, ProductRequest productRequest) throws IOException;

    void deleteProduct(Long id) throws IOException;

    ProductResponse getProductById(Long id);

    List<ProductResponse> getAllProducts();

    Page<ProductResponse> getProductsPage(Pageable pageable);

    List<ProductResponse> searchProductsByName(String name);

    List<ProductResponse> getProductsByCategory(Long categoryId);


    Page<ProductResponse> searchProducts(ProductSearchRequest searchRequest, Pageable pageable);
}