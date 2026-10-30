package com.store.api.model.dto.category;

import com.store.api.model.entity.Category;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CategoryResponse {
    private Long id;
    private String name;
    private String icon;
    private String color;
    private boolean system;
    private boolean active;

    public static CategoryResponse from(Category item) {
        if (item == null) return null;
        return CategoryResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .icon(item.getIcon())
                .color(item.getColor())
                .system(item.isSystem())
                .active(item.isActive())
                .build();
    }
}
