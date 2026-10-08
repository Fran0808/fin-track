package com.store.api.repository;

import com.store.api.model.entity.Category;
import com.store.api.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserOrderByNameAsc(User user);
    List<Category> findByUserAndActiveOrderByNameAsc(User user, boolean active);
    Optional<Category> findByIdAndUser(Long id, User user);
    boolean existsByUser(User user);
    boolean existsByUserAndParent(User user, Category parent);
    boolean existsByUserAndActiveTrueAndNameIgnoreCase(User user, String name);
}
