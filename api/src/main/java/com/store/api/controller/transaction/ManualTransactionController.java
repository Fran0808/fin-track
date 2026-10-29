package com.store.api.controller.transaction;

import com.store.api.model.dto.transaction.*;
import com.store.api.service.transaction.ManualTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class ManualTransactionController {
    private final ManualTransactionService service;

    @PostMapping("/manual")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody ManualTransactionRequest request) {
        return ResponseEntity.status(201).header("Cache-Control", "no-store").body(service.create(request));
    }

    @GetMapping("/{id}/manual-reconciliation")
    public ResponseEntity<ManualReconciliationStatus> status(@PathVariable Long id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.status(id));
    }

    @GetMapping("/manual/{id}/reconciliation-preview")
    public ResponseEntity<ManualReconciliationPreview> preview(@PathVariable Long id) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.preview(id));
    }

    @PostMapping("/manual/{id}/reconcile")
    public ResponseEntity<TransactionResponse> reconcile(@PathVariable Long id, @Valid @RequestBody ManualReconciliationRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.reconcile(id, request));
    }

    @DeleteMapping("/manual/{id}/reconciliation/{capturedId}")
    public ResponseEntity<TransactionResponse> undo(@PathVariable Long id, @PathVariable Long capturedId, @RequestParam Integer revision) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.undo(id, capturedId, revision));
    }
}
