package com.store.api.controller.category;

import com.store.api.model.dto.category.*;
import com.store.api.service.category.RuleHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/classification-rules/{id}")
@RequiredArgsConstructor
public class RuleHistoryController {
    private final RuleHistoryService service;

    @GetMapping("/history-preview")
    public ResponseEntity<RuleHistoryPreview> preview(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(id));
    }

    @PostMapping("/history-apply")
    public RuleHistoryApplyResponse apply(@PathVariable Long id, @Valid @RequestBody RuleHistoryApplyRequest request) {
        return service.apply(id, request);
    }

    @ExceptionHandler(org.springframework.dao.ConcurrencyFailureException.class)
    public ResponseEntity<java.util.Map<String, String>> concurrentChange() {
        return ResponseEntity.status(409).body(java.util.Map.of("message",
                "Los movimientos cambiaron durante la confirmación. Vuelve a revisar la vista previa."));
    }
}
