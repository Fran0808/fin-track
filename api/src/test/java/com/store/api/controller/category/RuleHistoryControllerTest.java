package com.store.api.controller.category;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.dto.category.*;
import com.store.api.service.category.RuleHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RuleHistoryControllerTest {
    private final RuleHistoryService service = mock(RuleHistoryService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new RuleHistoryController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void previewIsUncacheableAndReturnsReviewMetadata() throws Exception {
        when(service.preview(7L)).thenReturn(RuleHistoryPreview.builder().ruleId(7L).categoryName("Groceries")
                .transactions(List.of()).limit(200).hasMore(false).expiresAt(100L).previewToken("a".repeat(64)).build());
        mvc.perform(get("/api/v1/classification-rules/7/history-preview"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.ruleId").value(7)).andExpect(jsonPath("$.transactions").isArray())
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test void validatesApplyAndForwardsTheExactSignedSelection() throws Exception {
        for (String body : List.of("{}", "{\"transactionIds\":[]}",
                "{\"transactionIds\":[0,null],\"expiresAt\":1,\"previewToken\":\"invalid\"}")) {
            mvc.perform(post("/api/v1/classification-rules/7/history-apply").contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.apply(eq(7L),any())).thenReturn(new RuleHistoryApplyResponse(1,0));
        mvc.perform(post("/api/v1/classification-rules/7/history-apply").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[12],\"expiresAt\":123,\"previewToken\":\""+"a".repeat(64)+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appliedCount").value(1));
        verify(service).apply(eq(7L),argThat(r->r.getTransactionIds().equals(List.of(12L))));
    }

    @Test void concurrentChangesReturnAReviewableConflict() throws Exception {
        when(service.apply(eq(7L),any())).thenThrow(new org.springframework.dao.CannotSerializeTransactionException("synthetic"));
        mvc.perform(post("/api/v1/classification-rules/7/history-apply").contentType(MediaType.APPLICATION_JSON)
                .content("{\"transactionIds\":[12],\"expiresAt\":123,\"previewToken\":\""+"a".repeat(64)+"\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Vuelve a revisar")));
    }
}
