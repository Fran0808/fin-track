package com.store.api.model.dto.category;

import com.store.api.model.entity.ClassificationRule;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ClassificationRuleResponse {
    private Long id;
    private String matchText;
    private Long categoryId;
    private String categoryName;
    private boolean categoryActive;
    private int priority;
    private boolean enabled;

    public static ClassificationRuleResponse from(ClassificationRule rule) {
        return ClassificationRuleResponse.builder().id(rule.getId()).matchText(rule.getMatchText())
                .categoryId(rule.getCategory().getId()).categoryName(rule.getCategory().getName())
                .categoryActive(rule.getCategory().isActive()).priority(rule.getPriority())
                .enabled(rule.isEnabled()).build();
    }
}
