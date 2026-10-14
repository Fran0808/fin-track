package com.store.api.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "classification_rules", indexes = {
        @Index(name = "idx_rule_user_priority", columnList = "user_id,priority,id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassificationRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "match_text", nullable = false, length = 150)
    private String matchText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;
}
