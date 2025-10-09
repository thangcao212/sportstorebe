// Updated OrderStatus.java - Improved exception message to Vietnamese for better UX
package com.sprotshop.sportstore.Enum;

import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.exception.InvalidOrderTransitionException;

import java.util.Arrays;
import java.util.List;

public enum OrderStatus {
    PENDING,
    WAITING_FOR_PAYMENT,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELED;

    // Vietnamese mappings for friendly messages
    private static final java.util.Map<OrderStatus, String> VIETNAMESE_MAP = java.util.Map.of(
            PENDING, "Chờ xác nhận",
            WAITING_FOR_PAYMENT, "Chờ thanh toán",
            PROCESSING, "Đang xử lý",
            SHIPPED, "Đã gửi hàng",
            DELIVERED, "Đã giao hàng",
            COMPLETED, "Hoàn thành",
            CANCELED, "Đã hủy"
    );

    public static String getVietnamese(OrderStatus status) {
        return VIETNAMESE_MAP.getOrDefault(status, status.name());
    }

    /**
     * Refined validation based on flows:
     * - COD: PENDING -> PROCESSING (admin confirm), PROCESSING -> SHIPPED, SHIPPED -> DELIVERED, DELIVERED -> COMPLETED only if PAID.
     * - SEPAY: WAITING_FOR_PAYMENT -> PROCESSING only if PAID.
     * - CANCELED: From PENDING/WAITING_FOR_PAYMENT/PROCESSING/SHIPPED/DELIVERED if not COMPLETED.
     */
    public boolean canTransitionTo(OrderStatus next, PaymentMethod method, PaymentStatus currentPaymentStatus) {
        boolean valid = switch (this) {
            case PENDING -> {  // COD start
                if (method == PaymentMethod.COD) {
                    yield List.of(OrderStatus.PROCESSING, OrderStatus.CANCELED).contains(next);
                } else {  // SEPAY
                    yield next == OrderStatus.WAITING_FOR_PAYMENT || next == OrderStatus.CANCELED;
                }
            }
            case WAITING_FOR_PAYMENT -> {  // SEPAY only
                // Only to PROCESSING if PAID
                yield next == OrderStatus.PROCESSING && currentPaymentStatus == PaymentStatus.PAID;
            }
            case PROCESSING -> List.of(OrderStatus.SHIPPED, OrderStatus.CANCELED).contains(next);
            case SHIPPED -> List.of(OrderStatus.DELIVERED, OrderStatus.CANCELED).contains(next);
            case DELIVERED -> {
                if (method == PaymentMethod.COD) {
                    // ⚠️ COD: Chỉ cho phép sang COMPLETED khi thanh toán đã được xác nhận (PAID)
                    yield next == OrderStatus.COMPLETED && currentPaymentStatus == PaymentStatus.PAID;
                } else {
                    // 💳 SEPAY hoặc phương thức thanh toán online khác:
                    // Đơn hàng đã thanh toán từ trước, cho phép sang COMPLETED luôn
                    yield next == OrderStatus.COMPLETED;
                }
            }

            case COMPLETED -> false;  // Terminal
            case CANCELED -> false;   // Terminal
        };
        if (!valid) {
            // IMPROVED: Vietnamese friendly message
            String currentVN = getVietnamese(this);
            String nextVN = getVietnamese(next);
            String reason = switch (this) {
                case WAITING_FOR_PAYMENT -> "Cần thanh toán trước khi xử lý (SEPAY).";
                case DELIVERED -> method == PaymentMethod.COD ? "Cần xác nhận thanh toán COD trước." : "Đã hoàn thành quy trình.";
                default -> "Không hợp lệ theo quy trình đơn hàng.";
            };
            throw new InvalidOrderTransitionException("Không thể chuyển từ '" + currentVN + "' sang '" + nextVN + "'. Lý do: " + reason);
        }
        return valid;
    }

    public List<OrderStatus> getPossibleNextStates(PaymentMethod method, PaymentStatus payStatus) {
        return Arrays.stream(values())
                .filter(s -> {
                    try {
                        return this.canTransitionTo(s, method, payStatus);
                    } catch (InvalidOrderTransitionException e) {
                        return false;
                    }
                })
                .toList();
    }
}