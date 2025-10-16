package com.sprotshop.sportstore.exception;

public class InvalidCouponException extends IllegalStateException {
    public InvalidCouponException(String message) {
        super(message);
    }
}
