package com.store.api.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationRequest {

    @Size(max = 50, message = "La categoría no debe superar los 50 caracteres")
    private String category;

    private List<@Size(max = 30, message = "Cada etiqueta no debe superar los 30 caracteres") String> tags;

    @Size(max = 500, message = "Las notas no deben superar los 500 caracteres")
    private String notes;
}
