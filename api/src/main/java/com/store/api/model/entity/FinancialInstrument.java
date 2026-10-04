package com.store.api.model.entity;

import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "financial_instruments", indexes = {
        @Index(name = "idx_instrument_user_active", columnList = "user_id,active"),
        @Index(name = "idx_instrument_linked_account", columnList = "linked_account_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FinancialInstrument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private InstrumentType type;
    @Column(nullable = false, length = 100)
    private String alias;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Bank bank;
    @Column(name = "institution_name", length = 100)
    private String institutionName;
    @Column(name = "last_four", length = 4)
    private String lastFour;
    @Builder.Default @Column(nullable = false, length = 3)
    private String currency = "PEN";
    @Builder.Default @Column(nullable = false)
    private boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "linked_account_id")
    private FinancialInstrument linkedAccount;
}
