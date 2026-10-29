package com.store.api.controller.transaction;

import com.store.api.service.transaction.TransactionCorrectionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionCorrectionControllerTest {
    final TransactionCorrectionService service=mock(TransactionCorrectionService.class);
    final org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new TransactionCorrectionController(service)).build();
    final String valid="{\"requestId\":\"12345678-1234-1234-1234-123456789012\",\"expectedRevision\":0,\"reason\":\"Synthetic reason\",\"amount\":\"0.10\",\"transactionDate\":\"2026-10-01T12:00:00\",\"flowType\":\"INCOME\"}";
    @Test void rejectsInvalidMoneyDatesReasonsAndRevision() throws Exception {
        for(String payload:new String[]{"{}",valid.replace("0.10","0.001"),valid.replace("0.10","0.00"),
                valid.replace("0.10","100000000"),valid.replace("Synthetic reason"," "),valid.replace("\"expectedRevision\":0","\"expectedRevision\":-1"),
                valid.replace("INCOME","INVALID"),valid.replace("2026-10-01T12:00:00","bad"),valid.replace("12345678-1234-1234-1234-123456789012","bad")}) {
            mvc.perform(post("/api/v1/transactions/7/corrections").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void ownedHistoryAndWriteAreNotCached() throws Exception {
        mvc.perform(get("/api/v1/transactions/7/corrections?page=2")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).history(7L,2);
        mvc.perform(post("/api/v1/transactions/7/corrections").contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).correct(eq(7L),any());
    }
}
