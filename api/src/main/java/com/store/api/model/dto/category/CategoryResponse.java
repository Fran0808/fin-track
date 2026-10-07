package com.store.api.model.dto.category;

import com.store.api.model.entity.Category;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String icon;
    private String color;
    private boolean system;
    private boolean active;
    private Long parentId;
    @Builder.Default
    private List<CategoryResponse> subcategories = new ArrayList<>();

    public static CategoryResponse from(Category item) {
        if (item == null) return null;
        return CategoryResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .icon(item.getIcon())
                .color(item.getColor())
                .system(item.isSystem())
                .active(item.isActive())
                .parentId(item.getParent() == null ? null : item.getParent().getId())
                .subcategories(new ArrayList<>())
                .build();
    }
}
