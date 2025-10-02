package com.sprotshop.sportstore.service;

import com.sprotshop.sportstore.entity.Transaction;
import com.sprotshop.sportstore.request.SepayWebhookRequest;

public interface TransactionService {
    Transaction saveFromWebhook(SepayWebhookRequest webhook);
}
