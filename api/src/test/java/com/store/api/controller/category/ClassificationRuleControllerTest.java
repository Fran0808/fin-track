package com.store.api.controller.category;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.controller.transaction.TransactionController;
import com.store.api.model.dto.category.ClassificationRuleResponse;
import com.store.api.model.enums.PendingReason;
import com.store.api.service.category.ClassificationRuleService;
import com.store.api.service.instrument.FinancialInstrumentService;
import com.store.api.service.transaction.InstrumentAssignmentService;
import com.store.api.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClassificationRuleControllerTest {
    private final ClassificationRuleService rules = mock(ClassificationRuleService.class);
    private final TransactionService transactions = mock(TransactionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClassificationRuleController(rules),
                    new TransactionController(transactions, mock(InstrumentAssignmentService.class), mock(FinancialInstrumentService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void validatesRulePayloadAndReturnsCreatedRule() throws Exception {
        mvc.perform(post("/api/v1/classification-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matchText\":\" \",\"categoryId\":1,\"priority\":-1,\"enabled\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/classification-rules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rules);
        when(rules.create(any())).thenReturn(ClassificationRuleResponse.builder().id(1L)
                .matchText("CINE").categoryId(2L).categoryName("Ocio").categoryActive(true).enabled(true).priority(10).build());
        mvc.perform(post("/api/v1/classification-rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matchText\":\"CINE\",\"categoryId\":2,\"priority\":10,\"enabled\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoryId").value(2))
                .andExpect(jsonPath("$.categoryActive").value(true));
        verify(rules).create(argThat(request -> request.getCategoryId() == 2L && request.getEnabled()));
    }

    @Test void updatesAndDeletesRulesThroughExpectedEndpoints() throws Exception {
        mvc.perform(put("/api/v1/classification-rules/7").contentType(MediaType.APPLICATION_JSON)
                .content("{\"matchText\":\"CINE\",\"categoryId\":2,\"priority\":0,\"enabled\":false}"))
                .andExpect(status().isOk());
        verify(rules).update(eq(7L), argThat(request -> !request.getEnabled()));
        mvc.perform(delete("/api/v1/classification-rules/7")).andExpect(status().isNoContent());
        verify(rules).delete(7L);
    }

    @Test void pendingEndpointValidatesReasonAndPagination() throws Exception {
        when(transactions.getPendingTransactions(eq(PendingReason.CATEGORY), any()))
                .thenReturn(Page.empty(org.springframework.data.domain.PageRequest.of(2, 20)));
        mvc.perform(get("/api/v1/transactions/pending").param("reason", "CATEGORY").param("page", "2").param("size", "20"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        verify(transactions).getPendingTransactions(eq(PendingReason.CATEGORY), argThat(page ->
                page.getPageNumber() == 2 && page.getPageSize() == 20 && page.getSort().getOrderFor("id").isDescending()));
        clearInvocations(transactions);
        mvc.perform(get("/api/v1/transactions/pending").param("reason", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions/pending").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transactions/pending").param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(transactions);
    }
}
