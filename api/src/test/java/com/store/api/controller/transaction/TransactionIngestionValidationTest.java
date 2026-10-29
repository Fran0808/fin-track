package com.store.api.controller.transaction;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.service.transaction.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionIngestionValidationTest {
    @Test void invalidAmountsLengthsNullItemsAndOversizedBatchesAreBadRequests() throws Exception {
        var service = mock(TransactionService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String valid = "{\"amount\":1.25,\"flowType\":\"EXPENSE\",\"contactName\":\"Example\",\"transactionHash\":\"synthetic\",\"transactionDate\":\"2026-10-01T12:00:00\"}";
        for (String bad : java.util.List.of(valid.replace("1.25", "100000000.00"), valid.replace("1.25", "1.255"),
                valid.replace("Example", "x".repeat(151)))) {
            mvc.perform(post("/api/v1/transactions/sync").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        for (String bad : java.util.List.of("[]", "[null]", "[" + String.join(",", java.util.Collections.nCopies(101, valid)) + "]")) {
            mvc.perform(post("/api/v1/transactions/sync/batch").contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/transactions").param("size", "1000000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions").param("page", "-1")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
