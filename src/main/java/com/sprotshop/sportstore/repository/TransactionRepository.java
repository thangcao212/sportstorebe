package com.sprotshop.sportstore.repository;

import com.sprotshop.sportstore.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsBySepayTransactionId(Long sepayTransactionId);
    boolean existsByReferenceNumber(String referenceNumber);
}