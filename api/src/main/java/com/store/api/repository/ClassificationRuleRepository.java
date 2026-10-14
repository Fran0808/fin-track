package com.store.api.repository;

import com.store.api.model.entity.ClassificationRule;
import com.store.api.model.entity.Category;
import com.store.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule, Long> {
    List<ClassificationRule> findByUserOrderByPriorityAscIdAsc(User user);
    List<ClassificationRule> findByUserAndEnabledTrueOrderByPriorityAscIdAsc(User user);
    Optional<ClassificationRule> findByIdAndUser(Long id, User user);
    boolean existsByUserAndCategory(User user, Category category);
}
