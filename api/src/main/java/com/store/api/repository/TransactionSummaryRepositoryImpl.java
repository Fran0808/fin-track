package com.store.api.repository;

import com.store.api.model.dto.transaction.TransactionFilterSummary;
import com.store.api.model.entity.Transaction;
import com.store.api.model.enums.FlowType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.EnumMap;

@RequiredArgsConstructor
public class TransactionSummaryRepositoryImpl implements TransactionSummaryRepository {
    private final EntityManager entityManager;

    @Override
    public TransactionFilterSummary summarize(Specification<Transaction> specification) {
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createTupleQuery();
        var root = query.from(Transaction.class);
        query.multiselect(root.get("flowType"), cb.sum(root.<BigDecimal>get("amount")));
        query.where(specification.toPredicate(root, query, cb));
        query.groupBy(root.get("flowType"));
        var totals = new EnumMap<FlowType, BigDecimal>(FlowType.class);
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            totals.put(row.get(0, FlowType.class), row.get(1, BigDecimal.class));
        }
        return new TransactionFilterSummary(totals.getOrDefault(FlowType.EXPENSE, BigDecimal.ZERO),
                totals.getOrDefault(FlowType.INCOME, BigDecimal.ZERO),
                totals.getOrDefault(FlowType.INTERNAL_TRANSFER, BigDecimal.ZERO));
    }
}
