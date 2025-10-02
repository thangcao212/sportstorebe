// Updated TransactionServiceImpl.java - Minor fix for transferType comparison (use equalsIgnoreCase)
package com.sprotshop.sportstore.service.impl;

import com.sprotshop.sportstore.entity.Transaction;
import com.sprotshop.sportstore.repository.TransactionRepository;
import com.sprotshop.sportstore.request.SepayWebhookRequest;
import com.sprotshop.sportstore.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;

    @Override
    public Transaction saveFromWebhook(SepayWebhookRequest webhook) {
        // Check duplicate
        if (transactionRepository.existsBySepayTransactionId(webhook.getId()) ||
                transactionRepository.existsByReferenceNumber(webhook.getReferenceCode())) {
            throw new IllegalStateException("Duplicate: sepayId=" + webhook.getId() + " or ref=" + webhook.getReferenceCode());
        }

        BigDecimal amountIn = BigDecimal.ZERO;
        BigDecimal amountOut = BigDecimal.ZERO;
        if ("in".equalsIgnoreCase(webhook.getTransferType())) {
            amountIn = webhook.getTransferAmount();
        } else if ("out".equalsIgnoreCase(webhook.getTransferType())) {
            amountOut = webhook.getTransferAmount();
        }

        log.debug("Building transaction: amountIn={}, gateway={}", amountIn, webhook.getGateway());

        Transaction tx = Transaction.builder()
                .sepayTransactionId(webhook.getId())
                .gateway(webhook.getGateway())
                .transactionDate(webhook.getTransactionDate())
                .accountNumber(webhook.getAccountNumber())
                .subAccount(webhook.getSubAccount())
                .amountIn(amountIn)
                .amountOut(amountOut)
                .accumulated(webhook.getAccumulated())
                .code(webhook.getCode())
                .transactionContent(webhook.getContent())
                .referenceNumber(webhook.getReferenceCode())
                .body(webhook.getDescription())
                .build();

        Transaction saved = transactionRepository.save(tx);
        log.info("Saved new transaction: id={}, sepayId={}", saved.getId(), saved.getSepayTransactionId());
        return saved;
    }
}