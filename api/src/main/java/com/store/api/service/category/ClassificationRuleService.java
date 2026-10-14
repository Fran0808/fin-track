package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.ClassificationRuleRequest;
import com.store.api.model.dto.category.ClassificationRuleResponse;
import com.store.api.model.entity.*;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.CategoryRepository;
import com.store.api.repository.ClassificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ClassificationRuleService {
    private final ClassificationRuleRepository rules;
    private final CategoryRepository categories;

    @Transactional(readOnly = true)
    public List<ClassificationRuleResponse> list() {
        return rules.findByUserOrderByPriorityAscIdAsc(UserContext.requireCurrentUser()).stream()
                .map(ClassificationRuleResponse::from).toList();
    }

    @Transactional
    public ClassificationRuleResponse create(ClassificationRuleRequest request) {
        User user = UserContext.requireCurrentUser();
        return save(ClassificationRule.builder().user(user).build(), request, user);
    }

    @Transactional
    public ClassificationRuleResponse update(Long id, ClassificationRuleRequest request) {
        User user = UserContext.requireCurrentUser();
        return save(requireOwned(id, user), request, user);
    }

    @Transactional
    public void delete(Long id) {
        User user = UserContext.requireCurrentUser();
        rules.delete(requireOwned(id, user));
    }

    private ClassificationRuleResponse save(ClassificationRule rule, ClassificationRuleRequest request, User user) {
        Category category = categories.findByIdAndUser(request.getCategoryId(), user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Selecciona una categoría de tu catálogo."));
        // Allow pausing an existing rule whose category was subsequently deactivated.
        boolean keepsCategory = rule.getCategory() != null
                && rule.getCategory().getId().equals(category.getId());
        if (!category.isActive() && !(keepsCategory && !request.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una categoría activa.");
        }
        rule.setMatchText(request.getMatchText().trim());
        rule.setCategory(category);
        rule.setPriority(request.getPriority());
        rule.setEnabled(request.getEnabled());
        return ClassificationRuleResponse.from(rules.save(rule));
    }

    private ClassificationRule requireOwned(Long id, User user) {
        return rules.findByIdAndUser(id, user).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla no encontrada."));
    }

    @Transactional(readOnly = true)
    public void classifyNewTransaction(Transaction transaction) {
        User user = UserContext.requireCurrentUser();
        if (transaction.getUser() == null || !user.getId().equals(transaction.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado.");
        }
        if (transaction.getFlowType() == FlowType.INTERNAL_TRANSFER
                || (transaction.getCategory() != null && !transaction.getCategory().isBlank())) return;
        String contact = transaction.getContactName().toLowerCase(Locale.ROOT);
        for (ClassificationRule rule : rules.findByUserAndEnabledTrueOrderByPriorityAscIdAsc(user)) {
            Category category = rule.getCategory();
            if (category.isActive() && user.getId().equals(category.getUser().getId())
                    && contact.contains(rule.getMatchText().toLowerCase(Locale.ROOT))) {
                transaction.setCategory(category.getName());
                return;
            }
        }
    }
}
