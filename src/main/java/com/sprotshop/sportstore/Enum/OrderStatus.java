package com.sprotshop.sportstore.Enum;

import com.sprotshop.sportstore.Enum.PaymentMethod;
import com.sprotshop.sportstore.Enum.PaymentStatus;
import com.sprotshop.sportstore.exception.InvalidOrderTransitionException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public enum OrderStatus {
    PENDING,

    COMPLETED,
    CANCELED;


}