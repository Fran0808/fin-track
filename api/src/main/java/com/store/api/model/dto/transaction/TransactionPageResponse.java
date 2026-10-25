package com.store.api.model.dto.transaction;

import lombok.Getter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@Getter
public class TransactionPageResponse extends PageImpl<TransactionResponse> {
    private final TransactionFilterSummary summary;

    public TransactionPageResponse(Page<TransactionResponse> page, TransactionFilterSummary summary) {
        super(page.getContent(), page.getPageable(), page.getTotalElements());
        this.summary = summary;
    }
}
