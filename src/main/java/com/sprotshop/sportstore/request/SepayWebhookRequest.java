package com.sprotshop.sportstore.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class SepayWebhookRequest {
    private Long id;
    private String gateway;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transactionDate;
    private String accountNumber;
    private String subAccount;
    private String code;
    private String content;
    private String transferType;
    private BigDecimal transferAmount;
    private BigDecimal accumulated;
    private String referenceCode;
    private String description;
}