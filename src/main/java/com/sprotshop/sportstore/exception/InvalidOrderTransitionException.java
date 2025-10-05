package com.sprotshop.sportstore.exception;


public class InvalidOrderTransitionException extends IllegalStateException {
    public InvalidOrderTransitionException(String message) {
        super(message);
    }
}