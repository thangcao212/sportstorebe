// Updated Transaction.java - Add created_at field with @CreationTimestamp
package com.sprotshop.sportstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Khóa chính tự động tăng

    @Column(name = "sepay_transaction_id", unique = true)
    private Long sepayTransactionId; // Lưu id từ webhook SePay (ví dụ: 92704)

    private String gateway;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    private String accountNumber;

    private String subAccount;

    private BigDecimal amountIn;

    private BigDecimal amountOut;

    private BigDecimal accumulated;

    private String code;

    @Column(name = "transaction_content")
    private String transactionContent;

    @Column(name = "reference_number", unique = true)
    private String referenceNumber;

    private String body;

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDateTime createdAt; // Auto-set on insert
}