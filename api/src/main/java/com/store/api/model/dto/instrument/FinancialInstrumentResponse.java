package com.store.api.model.dto.instrument;

import com.store.api.model.entity.FinancialInstrument;
import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class FinancialInstrumentResponse {
    private Long id;
    private InstrumentType type;
    private String alias;
    private Bank bank;
    private String institutionName;
    private String lastFour;
    private String currency;
    private boolean active;
    private Long linkedAccountId;

    public static FinancialInstrumentResponse from(FinancialInstrument item) {
        if (item == null) return null;
        return FinancialInstrumentResponse.builder().id(item.getId()).type(item.getType())
                .alias(item.getAlias()).bank(item.getBank()).institutionName(item.getInstitutionName())
                .lastFour(item.getLastFour()).currency(item.getCurrency()).active(item.isActive())
                .linkedAccountId(item.getLinkedAccount() == null ? null : item.getLinkedAccount().getId()).build();
    }
}
