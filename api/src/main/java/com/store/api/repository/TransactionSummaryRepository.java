package com.store.api.repository;

import com.store.api.model.dto.transaction.TransactionFilterSummary;
import com.store.api.model.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

public interface TransactionSummaryRepository {
    TransactionFilterSummary summarize(Specification<Transaction> specification);
}
