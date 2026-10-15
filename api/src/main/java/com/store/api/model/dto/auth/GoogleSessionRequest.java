package com.store.api.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class GoogleSessionRequest {
    @NotBlank @Size(max = 100)
    private String code;
}
