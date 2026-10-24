package com.store.api.controller.transaction;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.PendingReason;
import com.store.api.service.instrument.FinancialInstrumentService;
import com.store.api.service.transaction.InstrumentAssignmentService;
import com.store.api.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionReviewControllerTest {
    private final TransactionService service = mock(TransactionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service,
            mock(InstrumentAssignmentService.class), mock(FinancialInstrumentService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @ParameterizedTest @EnumSource(PendingReason.class)
    void listingAndExportForwardTheSameCombinedFilters(PendingReason reason) throws Exception {
        LocalDateTime start = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-24T23:59:59");
        when(service.getTransactions(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("viaje"), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason), any()))
                .thenReturn(Page.empty(PageRequest.of(1, 10)));
        when(service.exportTransactionsCsv(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("viaje"), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason)))
                .thenReturn("ID,Fecha\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("startDate", start.toString()).param("endDate", end.toString())
                    .param("flowType", "EXPENSE").param("search", "CINE").param("tag", "viaje")
                    .param("channel", "YAPE").param("minAmount", "0.00").param("pendingReason", reason.name()).param("page", "1"))
                    .andExpect(status().isOk()).andExpect(header().string("X-Fintrack-Review-Filter", reason.name()));
        }
        verify(service).getTransactions(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("viaje"), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason),
                argThat(page -> page.getPageNumber() == 1 && page.getSort().getOrderFor("id").isDescending()));
        verify(service).exportTransactionsCsv(start, end, FlowType.EXPENSE, "CINE", null, null, "viaje", "YAPE", new BigDecimal("0.00"), null, reason);
    }

    @Test void rejectsUnknownReviewReasonsBeforeQuerying() throws Exception {
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("pendingReason", "INVALID")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void omittedReviewFilterPreservesOrdinaryListing() throws Exception {
        when(service.getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), any())).thenReturn(Page.empty(PageRequest.of(0, 10)));
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isOk())
                .andExpect(header().string("X-Fintrack-Review-Filter", "NONE"));
    }
}
