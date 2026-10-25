package com.store.api.model.entity;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import com.store.api.model.enums.CaptureOrigin;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_hash_user", columnList = "transaction_hash, user_id", unique = true),
        @Index(name = "idx_tx_date", columnList = "transaction_date"),
        @Index(name = "idx_tx_user", columnList = "user_id"),
        @Index(name = "idx_tx_user_instrument_date", columnList = "user_id,financial_instrument_id,transaction_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_instrument_id")
    private FinancialInstrument financialInstrument;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(name = "flow_type", nullable = false, length = 20)
    private FlowType flowType;
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 32)
    @Builder.Default
    private OperationType operationType = OperationType.UNKNOWN;
    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;
    @Column(name = "channel", nullable = false, length = 50)
    @Builder.Default
    private String channel = "YAPE";
    @Column(name = "card_last4", length = 10)
    private String cardLast4;
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "transaction_hash", nullable = false, length = 64)
    private String transactionHash;
    @Enumerated(EnumType.STRING)
    @Column(name = "capture_origin", nullable = false, length = 16)
    @Builder.Default
    private CaptureOrigin captureOrigin = CaptureOrigin.UNKNOWN;
    @Column(name = "operation_reference", length = 100)
    private String operationReference;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
