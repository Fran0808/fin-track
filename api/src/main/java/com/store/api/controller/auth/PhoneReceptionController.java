package com.store.api.controller.auth;

import com.store.api.model.dto.auth.PhoneReceptionStatusResponse;
import com.store.api.service.auth.PhoneReceptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PhoneReceptionController {
    private final PhoneReceptionService service;

    @GetMapping("/api/v1/user/phone-status")
    public ResponseEntity<PhoneReceptionStatusResponse> getStatus() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.getStatus());
    }
}
