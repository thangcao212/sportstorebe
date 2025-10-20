// File: com/sprotshop/sportstore/response/ProductReviewCount.java
package com.sprotshop.sportstore.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductReviewCount {
    private Long productId;
    private String productName;
    private Long reviewCount;
}