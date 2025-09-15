package com.sprotshop.sportstore.request;

import com.sprotshop.sportstore.Enum.OrderStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SearchOrderRequest {
    private Long userId;
    private OrderStatus status;
    private String provinceCode;
    private String districtCode;
    private String wardCode;
    private String keyword;

    private LocalDateTime fromDate;
    private LocalDateTime toDate;

    private Long productId;   // lọc theo sản phẩm cụ thể
    private Double minTotal;  // lọc theo tổng tiền min
    private Double maxTotal;  // lọc theo tổng tiền max
}
