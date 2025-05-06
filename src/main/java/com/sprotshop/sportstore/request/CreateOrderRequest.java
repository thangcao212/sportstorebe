package com.sprotshop.sportstore.request; // Thay đổi package nếu cần

import com.sprotshop.sportstore.Enum.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin cần thiết gửi từ FE khi người dùng đặt hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    // --- Thông tin giao hàng ---
    @NotBlank(message = "Tên người nhận không được trống")
    private String shippingRecipientName;

    @NotBlank(message = "Số điện thoại người nhận không được trống")
    private String shippingPhone;

    @NotBlank(message = "Địa chỉ đường không được trống")
    private String shippingStreet;

    @NotBlank(message = "Phường/Xã không được trống")
    private String shippingWard;

    @NotBlank(message = "Quận/Huyện không được trống")
    private String shippingDistrict;

    @NotBlank(message = "Tỉnh/Thành phố không được trống")
    private String shippingCity;

    // --- Thông tin khác ---
    private String notes; // Ghi chú (tùy chọn)

    @NotNull(message = "Phương thức thanh toán không được trống") // << Thêm NotNull
    private PaymentMethod paymentMethod; // << Đổi thành Enum
}