package com.store.api.controller.transaction;

import com.store.api.model.dto.transaction.TransactionOriginResponse;
import com.store.api.service.transaction.TransactionOriginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionOriginController {
    private final TransactionOriginService service;

    @GetMapping("/{id}/origin")
    public ResponseEntity<TransactionOriginResponse> getOrigin(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getOrigin(id));
    }
}
