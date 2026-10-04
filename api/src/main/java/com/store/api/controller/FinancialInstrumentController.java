package com.store.api.controller;

import com.store.api.model.dto.FinancialInstrumentRequest;
import com.store.api.model.dto.FinancialInstrumentResponse;
import com.store.api.service.FinancialInstrumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/financial-instruments") @RequiredArgsConstructor
public class FinancialInstrumentController {
    private final FinancialInstrumentService service;

    @GetMapping
    public List<FinancialInstrumentResponse> list(@RequestParam(required = false) Boolean active) {
        return service.list(active);
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public FinancialInstrumentResponse create(@Valid @RequestBody FinancialInstrumentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public FinancialInstrumentResponse update(@PathVariable Long id, @Valid @RequestBody FinancialInstrumentRequest request) {
        return service.update(id, request);
    }
}
