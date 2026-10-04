package com.store.api.service;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.CategoryRequest;
import com.store.api.model.dto.CategoryResponse;
import com.store.api.model.entity.Category;
import com.store.api.model.entity.User;
import com.store.api.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryServiceTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final CategoryService service = new CategoryService(repository);
    private final User user = User.builder().id(12L).email("tester@example.test").build();

    @BeforeEach
    public void setup() {
        UserContext.setCurrentUser(user);
        when(repository.save(any(Category.class))).thenAnswer(invocation -> {
            Category cat = invocation.getArgument(0);
            if (cat.getId() == null) cat.setId(100L);
            return cat;
        });
    }

    @AfterEach
    public void tearDown() {
        UserContext.clear();
    }

    @Test
    void anonymousCannotListCreateUpdateToggleOrDelete() {
        UserContext.clear();
        CategoryRequest req = new CategoryRequest();
        req.setName("Test");

        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.list(null)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.create(req)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.update(1L, req)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.toggleActive(1L)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.delete(1L)).getStatusCode().value());
    }

    @Test
    void listInitializesDefaultCategoriesIfUserHasNone() {
        when(repository.existsByUser(user)).thenReturn(false);
        when(repository.findByUserOrderByNameAsc(user)).thenReturn(List.of(
                Category.builder().id(1L).user(user).name("Comida y bebidas").system(true).active(true).build(),
                Category.builder().id(2L).user(user).name("Auto y movilidad").system(true).active(true).build()
        ));

        List<CategoryResponse> results = service.list(null);
        verify(repository).saveAll(argThat(iterable -> {
            List<Category> list = new ArrayList<>();
            iterable.forEach(list::add);
            return list.size() == 25;
        }));
        assertEquals(2, results.size());
    }

    @Test
    void createAddsCustomCategoryForUser() {
        when(repository.existsByUser(user)).thenReturn(true);

        CategoryRequest req = new CategoryRequest();
        req.setName("Proyectos Freelance");
        req.setIcon("briefcase");
        req.setColor("#10B981");

        CategoryResponse res = service.create(req);
        assertNotNull(res);
        assertEquals("Proyectos Freelance", res.getName());
        assertEquals("briefcase", res.getIcon());
        assertEquals("#10B981", res.getColor());
        assertFalse(res.isSystem());
        assertTrue(res.isActive());

        verify(repository).save(argThat(cat ->
                cat.getUser().equals(user) &&
                cat.getName().equals("Proyectos Freelance") &&
                !cat.isSystem()
        ));
    }

    @Test
    void createSubcategorySetsParentCorrectly() {
        when(repository.existsByUser(user)).thenReturn(true);
        Category parent = Category.builder().id(5L).user(user).name("Comida y bebidas").build();
        when(repository.findByIdAndUser(5L, user)).thenReturn(Optional.of(parent));

        CategoryRequest req = new CategoryRequest();
        req.setName("Restaurantes");
        req.setParentId(5L);

        CategoryResponse res = service.create(req);
        assertEquals(5L, res.getParentId());
        verify(repository).save(argThat(cat -> cat.getParent() != null && cat.getParent().getId().equals(5L)));
    }

    @Test
    void toggleActiveInvertsState() {
        Category cat = Category.builder().id(8L).user(user).name("Delivery").system(true).active(true).build();
        when(repository.findByIdAndUser(8L, user)).thenReturn(Optional.of(cat));

        CategoryResponse res = service.toggleActive(8L);
        assertFalse(res.isActive());
        verify(repository).save(argThat(c -> !c.isActive()));
    }

    @Test
    void cannotDeleteSystemCategory() {
        Category systemCat = Category.builder().id(3L).user(user).name("Salud").system(true).active(true).build();
        when(repository.findByIdAndUser(3L, user)).thenReturn(Optional.of(systemCat));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(3L));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("System categories cannot be deleted"));
        verify(repository, never()).delete(any());
    }

    @Test
    void cannotDeleteCategoryWithSubcategories() {
        Category customCat = Category.builder().id(15L).user(user).name("Hobbies").system(false).active(true).build();
        when(repository.findByIdAndUser(15L, user)).thenReturn(Optional.of(customCat));
        when(repository.existsByUserAndParent(user, customCat)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.delete(15L));
        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("subcategories"));
        verify(repository, never()).delete(any());
    }

    @Test
    void canDeleteCustomCategoryWithoutSubcategories() {
        Category customCat = Category.builder().id(15L).user(user).name("Hobbies").system(false).active(true).build();
        when(repository.findByIdAndUser(15L, user)).thenReturn(Optional.of(customCat));
        when(repository.existsByUserAndParent(user, customCat)).thenReturn(false);

        assertDoesNotThrow(() -> service.delete(15L));
        verify(repository).delete(customCat);
    }

    @Test
    void foreignCategoryCannotBeAccessedOrModified() {
        when(repository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

        CategoryRequest req = new CategoryRequest();
        req.setName("Hacked");

        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.update(99L, req)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.toggleActive(99L)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.delete(99L)).getStatusCode().value());
    }
}
