package com.store.api.controller.transaction;

import com.store.api.model.dto.transaction.*;
import com.store.api.service.transaction.TransactionCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/transactions/{id}/corrections") @RequiredArgsConstructor
public class TransactionCorrectionController {
    private final TransactionCorrectionService service;

    @GetMapping
    public ResponseEntity<TransactionCorrectionHistory> history(@PathVariable Long id, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.history(id, page));
    }
    @PostMapping
    public ResponseEntity<TransactionResponse> correct(@PathVariable Long id, @Valid @RequestBody TransactionCorrectionRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.correct(id, request));
    }
}
