// Updated OrderStatus.java - Refined canTransitionTo logic to match described flows.
// COD: PENDING -> PROCESSING (admin confirm), PROCESSING -> SHIPPED, SHIPPED -> DELIVERED, DELIVERED -> COMPLETED only if PAID.
// SEPAY: WAITING_FOR_PAYMENT -> PROCESSING only if PAID, then same as COD post-PROCESSING.
// Added CANCELED from early states if PENDING.
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

    /**
     * Refined validation based on flows:
     * - COD: PENDING -> PROCESSING (any payment), PROCESSING/SHIPPED -> next, DELIVERED -> COMPLETED only if PAID.
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
                    // COD: Need PAID to COMPLETE
                    yield next == OrderStatus.COMPLETED && currentPaymentStatus == PaymentStatus.PAID;
                } else {  // SEPAY: Already PAID, auto/manual COMPLETE
                    yield next == OrderStatus.COMPLETED;
                }
            }
            case COMPLETED -> false;  // Terminal
            case CANCELED -> false;   // Terminal
        };
        if (!valid) {
            throw new InvalidOrderTransitionException("Invalid transition from " + this + " to " + next +
                    " (method: " + method + ", payment: " + currentPaymentStatus + "). Check flow rules.");
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