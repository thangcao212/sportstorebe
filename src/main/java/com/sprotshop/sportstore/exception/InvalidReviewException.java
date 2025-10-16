package com.sprotshop.sportstore.exception;

public class InvalidReviewException extends IllegalArgumentException {
    public InvalidReviewException(String message) {
        super(message);
    }
}