package com.sprotshop.sportstore.service; // Thay đổi package nếu cần

import com.sprotshop.sportstore.Enum.OrderStatus;
import com.sprotshop.sportstore.request.CreateOrderRequest;
import com.sprotshop.sportstore.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Interface định nghĩa các dịch vụ liên quan đến Đơn hàng.
 * Các phương thức hoạt động trên ngữ cảnh người dùng đang đăng nhập,
 * trừ các phương thức dành riêng cho Admin.
 */
public interface OrderService {

    /**
     * Tạo đơn hàng mới từ giỏ hàng của user đang đăng nhập.
     * Thực hiện kiểm tra tồn kho, lưu đơn hàng, cập nhật tồn kho, xóa giỏ hàng.
     * @param request Chứa thông tin giao hàng, phương thức thanh toán...
     * @return Chi tiết đơn hàng vừa tạo.
     */
    OrderResponse createOrderFromCart(CreateOrderRequest request);

    /** Lấy lịch sử đơn hàng của user đang đăng nhập (phân trang). */
    Page<OrderResponse> getUserOrders(Pageable pageable);

    /** Lấy chi tiết đơn hàng của user đang đăng nhập (kiểm tra quyền sở hữu). */
    OrderResponse getOrderDetails(Long orderId);

    /** Hủy đơn hàng của user đang đăng nhập (kiểm tra quyền sở hữu và trạng thái). */
    OrderResponse cancelOrder(Long orderId);

    // --- Admin Functions ---

    /** [Admin] Cập nhật trạng thái đơn hàng. */
    OrderResponse updateOrderStatus(Long orderId, OrderStatus newStatus);

    /** [Admin] Thêm/Cập nhật mã vận đơn. */
    OrderResponse addTrackingNumber(Long orderId, String trackingNumber);

    /** [Admin] Lấy chi tiết đơn hàng bất kỳ. */
    OrderResponse getOrderByIdForAdmin(Long orderId);

    /** [Admin] Lấy tất cả đơn hàng (phân trang). */
    Page<OrderResponse> getAllOrders(Pageable pageable);
}