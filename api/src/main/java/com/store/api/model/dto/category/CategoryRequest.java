package com.store.api.model.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 50)
    private String icon;

    @Size(max = 20)
    private String color;

    private Long parentId;

    private Boolean active;
}
