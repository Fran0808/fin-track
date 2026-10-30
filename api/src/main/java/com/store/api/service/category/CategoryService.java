package com.store.api.service.category;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.category.CategoryRequest;
import com.store.api.model.dto.category.CategoryResponse;
import com.store.api.model.entity.Category;
import com.store.api.model.entity.User;
import com.store.api.repository.CategoryRepository;
import com.store.api.repository.ClassificationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository repository;
    private final ClassificationRuleRepository classificationRules;

    private static final List<SystemCategoryTemplate> DEFAULT_CATEGORIES = List.of(
            new SystemCategoryTemplate("Ahorro e inversión", "piggy-bank", "#10B981"),
            new SystemCategoryTemplate("Apoyo a terceros", "heart-handshake", "#EC4899"),
            new SystemCategoryTemplate("Auto y movilidad", "car", "#3B82F6"),
            new SystemCategoryTemplate("Comida y bebidas", "utensils", "#F97316"),
            new SystemCategoryTemplate("Compras personales", "shopping-bag", "#A855F7"),
            new SystemCategoryTemplate("Cuidado personal", "sparkles", "#F43F5E"),
            new SystemCategoryTemplate("Delivery", "bike", "#FB923C"),
            new SystemCategoryTemplate("Deporte y fitness", "dumbbell", "#E11D48"),
            new SystemCategoryTemplate("Educación", "graduation-cap", "#64748B"),
            new SystemCategoryTemplate("Hijos y familia", "users", "#F472B6"),
            new SystemCategoryTemplate("Mascotas", "paw-print", "#D97706"),
            new SystemCategoryTemplate("Salud y farmacia", "heart-pulse", "#EF4444"),
            new SystemCategoryTemplate("Servicios y suscripciones", "tv", "#6366F1"),
            new SystemCategoryTemplate("Salidas y entretenimiento", "ticket", "#8B5CF6"),
            new SystemCategoryTemplate("Hogar y compras", "home", "#0EA5E9"),
            new SystemCategoryTemplate("Tecnología y electrónica", "laptop", "#06B6D4"),
            new SystemCategoryTemplate("Viajes y turismo", "plane", "#14B8A6"),
            new SystemCategoryTemplate("Ropa y calzado", "shirt", "#84CC16"),
            new SystemCategoryTemplate("Trabajo y negocios", "briefcase", "#475569"),
            new SystemCategoryTemplate("Impuestos y trámites", "file-text", "#6B7280"),
            new SystemCategoryTemplate("Donaciones y caridad", "gift", "#DB2777"),
            new SystemCategoryTemplate("Seguros", "shield-check", "#2563EB"),
            new SystemCategoryTemplate("Bares y vida nocturna", "wine", "#9333EA"),
            new SystemCategoryTemplate("Transferencias y finanzas", "arrow-left-right", "#059669"),
            new SystemCategoryTemplate("Otros gastos", "help-circle", "#94A3B8")
    );

    @Transactional
    public List<CategoryResponse> list(Boolean active) {
        User user = UserContext.requireCurrentUser();
        ensureDefaultCategories(user);

        List<Category> categories = active != null
                ? repository.findByUserAndActiveOrderByNameAsc(user, active)
                : repository.findByUserOrderByNameAsc(user);

        return categories.stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        User user = UserContext.requireCurrentUser();
        ensureDefaultCategories(user);

        String trimmedName = request.getName() == null ? "" : request.getName().trim();
        if (trimmedName.isBlank()) {
            throw badRequest("Category name cannot be blank");
        }

        Category category = Category.builder()
                .user(user)
                .name(trimmedName)
                .icon(request.getIcon() != null && !request.getIcon().isBlank() ? request.getIcon().trim() : "tag")
                .color(request.getColor() != null && !request.getColor().isBlank() ? request.getColor().trim() : "#10B981")
                .system(false)
                .active(request.getActive() == null || request.getActive())
                .build();

        return CategoryResponse.from(repository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        User user = UserContext.requireCurrentUser();
        Category item = requireOwned(id, user);

        String trimmedName = request.getName() == null ? "" : request.getName().trim();
        if (trimmedName.isBlank()) {
            throw badRequest("Category name cannot be blank");
        }

        item.setName(trimmedName);
        if (request.getIcon() != null && !request.getIcon().isBlank()) {
            item.setIcon(request.getIcon().trim());
        }
        if (request.getColor() != null && !request.getColor().isBlank()) {
            item.setColor(request.getColor().trim());
        }
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }

        return CategoryResponse.from(repository.save(item));
    }

    @Transactional
    public CategoryResponse toggleActive(Long id) {
        User user = UserContext.requireCurrentUser();
        Category item = requireOwned(id, user);
        item.setActive(!item.isActive());
        return CategoryResponse.from(repository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        User user = UserContext.requireCurrentUser();
        Category item = requireOwned(id, user);

        if (item.isSystem()) {
            throw badRequest("System categories cannot be deleted; you can deactivate them instead");
        }

        if (classificationRules.existsByUserAndCategory(user, item)) {
            throw badRequest("Elimina o cambia las reglas que usan esta categoría antes de eliminarla.");
        }
        repository.delete(item);
    }

    @Transactional(readOnly = true)
    public Category requireOwned(Long id, User user) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private void ensureDefaultCategories(User user) {
        if (repository.existsByUser(user)) {
            return;
        }

        List<Category> templates = new ArrayList<>(DEFAULT_CATEGORIES.size());
        for (SystemCategoryTemplate template : DEFAULT_CATEGORIES) {
            templates.add(Category.builder()
                    .user(user)
                    .name(template.name)
                    .icon(template.icon)
                    .color(template.color)
                    .system(true)
                    .active(true)
                    .build());
        }
        repository.saveAll(templates);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record SystemCategoryTemplate(String name, String icon, String color) {}
}
