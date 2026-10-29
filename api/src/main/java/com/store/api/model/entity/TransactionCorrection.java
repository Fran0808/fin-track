package com.store.api.model.entity;

import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_corrections", uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_id", "request_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionCorrection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;
    @Column(name = "expected_revision", nullable = false)
    private long expectedRevision;
    @Column(nullable = false, length = 300)
    private String reason;
    @Column(name = "before_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal beforeAmount;
    @Column(name = "after_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal afterAmount;
    @Column(name = "before_date", nullable = false)
    private LocalDateTime beforeDate;
    @Column(name = "after_date", nullable = false)
    private LocalDateTime afterDate;
    @Enumerated(EnumType.STRING) @Column(name = "before_flow", nullable = false, length = 20)
    private FlowType beforeFlow;
    @Enumerated(EnumType.STRING) @Column(name = "after_flow", nullable = false, length = 20)
    private FlowType afterFlow;
    @Enumerated(EnumType.STRING) @Column(name = "before_operation", nullable = false, length = 32)
    private OperationType beforeOperation;
    @Enumerated(EnumType.STRING) @Column(name = "after_operation", nullable = false, length = 32)
    private OperationType afterOperation;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
