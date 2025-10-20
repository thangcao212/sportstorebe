// File: com/sprotshop/sportstore/response/SentimentStats.java
package com.sprotshop.sportstore.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SentimentStats {
    private Long positive; // 4-5 stars
    private Long neutral; // 3 stars
    private Long negative; // 1-2 stars
}