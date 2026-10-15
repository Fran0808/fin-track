package com.store.api.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class GoogleSessionResponse {
    private String token;
}
