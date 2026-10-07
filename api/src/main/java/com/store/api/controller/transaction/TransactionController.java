package com.store.api.controller.transaction;

import com.store.api.model.dto.transaction.TransactionClassificationRequest;
import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.dto.instrument.FinancialInstrumentResponse;
import com.store.api.model.dto.transaction.InstrumentAssignmentRequest;
import com.store.api.config.security.UserContext;
import com.store.api.model.enums.FlowType;
import com.store.api.service.transaction.TransactionService;
import com.store.api.service.instrument.FinancialInstrumentService;
import com.store.api.service.transaction.InstrumentAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final InstrumentAssignmentService assignmentService;
    private final FinancialInstrumentService instrumentService;

    @PatchMapping("/{id}/financial-instrument")
    public TransactionResponse assignInstrument(@PathVariable Long id,
            @Valid @RequestBody InstrumentAssignmentRequest request) {
        return assignmentService.assign(id, request.getFinancialInstrumentId());
    }

    @PatchMapping("/{id}/classification")
    public TransactionResponse updateClassification(
            @PathVariable Long id,
            @Valid @RequestBody TransactionClassificationRequest request
    ) {
        return transactionService.updateClassification(id, request);
    }

    @GetMapping("/{id}/financial-instrument-suggestions")
    public List<FinancialInstrumentResponse> instrumentSuggestions(@PathVariable Long id) {
        return assignmentService.suggestions(id);
    }

    // Single transaction ingestion from mobile device
    @PostMapping("/sync")
    public ResponseEntity<TransactionResponse> syncTransaction(@Valid @RequestBody TransactionSyncRequest request) {
        TransactionResponse response = transactionService.processAndSave(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    // Batch transaction ingestion (when recovering connection after offline mode)
    @PostMapping("/sync/batch")
    public ResponseEntity<List<TransactionResponse>> syncBatchTransactions(@RequestBody List<@Valid TransactionSyncRequest> requests) {
        List<TransactionResponse> responses = transactionService.processBatch(requests);
        return new ResponseEntity<>(responses, HttpStatus.CREATED);
    }

    // Paginated list with dynamic filters for the Dashboard
    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) FlowType flowType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long financialInstrumentId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        if (financialInstrumentId != null) {
            instrumentService.requireOwned(financialInstrumentId, UserContext.requireCurrentUser());
        }
        Page<TransactionResponse> result = transactionService.getTransactions(
                startDate, endDate, flowType, search, financialInstrumentId, category, tag, channel, minAmount, maxAmount, pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) FlowType flowType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long financialInstrumentId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount
    ) {
        if (financialInstrumentId != null) {
            instrumentService.requireOwned(financialInstrumentId, UserContext.requireCurrentUser());
        }
        byte[] csvBytes = transactionService.exportTransactionsCsv(
                startDate, endDate, flowType, search, financialInstrumentId, category, tag, channel, minAmount, maxAmount
        );
        String filename = "fintrack_movimientos_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }
}
