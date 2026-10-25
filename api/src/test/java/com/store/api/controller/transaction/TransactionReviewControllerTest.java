package com.store.api.controller.transaction;

import com.store.api.config.GlobalExceptionHandler;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.PendingReason;
import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.service.instrument.FinancialInstrumentService;
import com.store.api.service.transaction.InstrumentAssignmentService;
import com.store.api.service.transaction.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionReviewControllerTest {
    private final TransactionService service = mock(TransactionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service,
            mock(InstrumentAssignmentService.class), mock(FinancialInstrumentService.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void ownCardPaymentRepairReturnsTheUpdatedMovement() throws Exception {
        when(service.markOwnCardPayment(7L)).thenReturn(TransactionResponse.builder()
                .id(7L).flowType(FlowType.INTERNAL_TRANSFER)
                .operationType(com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT).amount(new BigDecimal("81.25")).build());
        mvc.perform(patch("/api/v1/transactions/7/own-card-payment"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.flowType").value("INTERNAL_TRANSFER"))
                .andExpect(jsonPath("$.operationType").value("OWN_CARD_PAYMENT"))
                .andExpect(jsonPath("$.amount").value(81.25));
        verify(service).markOwnCardPayment(7L);
    }

    @ParameterizedTest @EnumSource(PendingReason.class)
    void listingAndExportForwardTheSameCombinedFilters(PendingReason reason) throws Exception {
        LocalDateTime start = LocalDateTime.parse("2026-09-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-24T23:59:59");
        when(service.getTransactions(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason), isNull(), isNull(), any()))
                .thenReturn(Page.empty(PageRequest.of(1, 10)));
        when(service.exportTransactionsCsv(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason), isNull(), isNull()))
                .thenReturn("ID,Fecha\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("startDate", start.toString()).param("endDate", end.toString())
                    .param("flowType", "EXPENSE").param("search", "CINE")
                    .param("channel", "YAPE").param("minAmount", "0.00").param("pendingReason", reason.name()).param("page", "1"))
                    .andExpect(status().isOk()).andExpect(header().string("X-Fintrack-Review-Filter", reason.name()));
        }
        verify(service).getTransactions(eq(start), eq(end), eq(FlowType.EXPENSE), eq("CINE"), isNull(),
                isNull(), eq("YAPE"), eq(new BigDecimal("0.00")), isNull(), eq(reason), isNull(), isNull(),
                argThat(page -> page.getPageNumber() == 1 && page.getSort().getOrderFor("id").isDescending()));
        verify(service).exportTransactionsCsv(start, end, FlowType.EXPENSE, "CINE", null, null, "YAPE", new BigDecimal("0.00"), null, reason, null, null);
    }

    @Test void merchantFilterIsForwardedAndAcknowledgedForListingAndCsv() throws Exception {
        when(service.getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("Exact Shop"), isNull(), any())).thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(service.exportTransactionsCsv(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("Exact Shop"), isNull())).thenReturn(new byte[0]);
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("merchantName", "Exact Shop")).andExpect(status().isOk())
                    .andExpect(header().string("X-Fintrack-Merchant-Filter", "APPLIED"));
        }
        verify(service).getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), eq("Exact Shop"), isNull(), any());
        verify(service).exportTransactionsCsv(null, null, null, null, null, null, null, null, null, null, "Exact Shop", null);
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings = {"Ocio", ""})
    void categoryGroupIsForwardedIncludingUncategorized(String group) throws Exception {
        when(service.getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(group), any())).thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(service.exportTransactionsCsv(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(group))).thenReturn(new byte[0]);
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("categoryGroup", group)).andExpect(status().isOk())
                    .andExpect(header().string("X-Fintrack-Category-Filter", "APPLIED"));
        }
        verify(service).getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(group), any());
        verify(service).exportTransactionsCsv(null, null, null, null, null, null, null, null, null, null, null, group);
    }

    @Test void rejectsUnknownReviewReasonsBeforeQuerying() throws Exception {
        for (String path : new String[]{"/api/v1/transactions", "/api/v1/transactions/export"}) {
            mvc.perform(get(path).param("pendingReason", "INVALID")).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void omittedReviewFilterPreservesOrdinaryListing() throws Exception {
        when(service.getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(Page.empty(PageRequest.of(0, 10)));
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isOk())
                .andExpect(header().string("X-Fintrack-Review-Filter", "NONE"));
    }

    @Test void listingAndClassificationExposeCategoryWithoutRetiredMetadata() throws Exception {
        var movement = TransactionResponse.builder().id(1L).category("Ocio").amount(new BigDecimal("12.34")).build();
        when(service.getTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(movement), PageRequest.of(0, 10), 1));
        when(service.updateClassification(eq(1L), any())).thenReturn(movement);
        mvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].category").value("Ocio"))
                .andExpect(jsonPath("$.content[0].tags").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content[0].notes").doesNotHaveJsonPath());
        mvc.perform(patch("/api/v1/transactions/1/classification").contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Ocio\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("Ocio"))
                .andExpect(jsonPath("$.tags").doesNotHaveJsonPath()).andExpect(jsonPath("$.notes").doesNotHaveJsonPath());
        verify(service).updateClassification(eq(1L), argThat(request -> "Ocio".equals(request.getCategory())));
    }
}
