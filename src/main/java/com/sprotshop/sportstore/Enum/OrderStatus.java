package com.sprotshop.sportstore.Enum;

/**
 * Enum representing the status of an order
 */
public enum OrderStatus {
    PENDING,              // Mới tạo, chờ xử lý/thanh toán
    WAITING_FOR_PAYMENT,  // Chờ thanh toán (vd: chuyển khoản, cổng thanh toán)
    PAYMENT_FAILED,       // Thanh toán thất bại
    PROCESSING,           // Đã thanh toán / Đã xác nhận, đang chuẩn bị hàng
    SHIPPED,              // Đã đóng gói, bàn giao cho đơn vị vận chuyển
    DELIVERED,            // Đã giao hàng thành công đến người nhận
    CANCELED,             // Đơn hàng đã bị hủy (bởi khách hoặc cửa hàng)
    RETURNED

} // Đơn hàng đã được hoàn trả (một phần hoặc toàn bộ)