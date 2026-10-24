package com.store.api.model.dto.transaction;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationRequest {

    @Size(max = 100, message = "La categoría no debe superar los 100 caracteres")
    private String category;
}
