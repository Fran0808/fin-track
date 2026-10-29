package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.*;
import com.store.api.model.entity.*;
import com.store.api.model.enums.*;
import com.store.api.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionCorrectionServiceTest {
    final TransactionRepository transactions = mock(TransactionRepository.class);
    final TransactionCorrectionRepository corrections = mock(TransactionCorrectionRepository.class);
    final TransactionCorrectionService service = new TransactionCorrectionService(transactions, corrections);
    final User owner = User.builder().id(1L).build();
    final Transaction row = Transaction.builder().id(7L).user(owner).amount(new BigDecimal("150.00"))
            .flowType(FlowType.EXPENSE).transactionDate(LocalDateTime.of(2026,10,1,12,0))
            .contactName("Synthetic store").channel("CASH").transactionHash("original-hash")
            .captureOrigin(CaptureOrigin.EMAIL).operationReference("SYN-REF").category("Synthetic category").build();

    static TransactionCorrectionRequest request() {
        var req = new TransactionCorrectionRequest(); req.setRequestId(UUID.randomUUID().toString()); req.setExpectedRevision(0L);
        req.setAmount(new BigDecimal("15.00")); req.setTransactionDate(LocalDateTime.of(2026,9,30,11,0));
        req.setFlowType(FlowType.INCOME); req.setReason(" Synthetic correction "); return req;
    }
    @BeforeEach void setup() {
        UserContext.setCurrentUser(owner);
        when(transactions.findLockedByIdAndUser(7L,owner)).thenReturn(Optional.of(row));
        when(transactions.findByIdAndUser(7L,owner)).thenReturn(Optional.of(row));
        when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void rejectsAnonymousAndForeignRecordsBeforeAnyAuditRead() {
        UserContext.clear();
        assertEquals(401,assertThrows(ResponseStatusException.class,()->service.correct(7L,request())).getStatusCode().value());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->service.history(7L,0)).getStatusCode().value());
        UserContext.setCurrentUser(owner);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.correct(99L,request())).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.history(99L,0)).getStatusCode().value());
        verifyNoInteractions(corrections);
    }
    @Test void retainsOriginalValuesAndIdentityWhileUpdatingEffectiveFields() {
        var req=request(); var result=service.correct(7L,req);
        var audit=org.mockito.ArgumentCaptor.forClass(TransactionCorrection.class); verify(corrections).save(audit.capture());
        assertEquals(new BigDecimal("150.00"),audit.getValue().getBeforeAmount());
        assertEquals(FlowType.EXPENSE,audit.getValue().getBeforeFlow()); assertEquals(owner,audit.getValue().getUser());
        assertEquals("Synthetic correction",audit.getValue().getReason()); assertNotNull(audit.getValue().getCreatedAt());
        assertEquals(req.getAmount(),result.getAmount()); assertEquals(req.getTransactionDate(),result.getTransactionDate());
        assertEquals(FlowType.INCOME,result.getFlowType()); assertEquals("original-hash",result.getTransactionHash());
        assertEquals(CaptureOrigin.EMAIL,result.getCaptureOrigin()); assertEquals("SYN-REF",row.getOperationReference());
        assertEquals("Synthetic category",result.getCategory()); assertEquals("CASH",result.getChannel());
        assertEquals(1,row.getReconciliationRevision());
    }
    @Test void clearsOwnPaymentInterpretationOnlyWhenChangingFlow() {
        row.setFlowType(FlowType.INTERNAL_TRANSFER); row.setOperationType(OperationType.OWN_CARD_PAYMENT);
        var req=request(); req.setFlowType(FlowType.INTERNAL_TRANSFER); service.correct(7L,req);
        assertEquals(OperationType.OWN_CARD_PAYMENT,row.getOperationType());
        req.setFlowType(FlowType.EXPENSE); service.correct(7L,req);
        assertEquals(OperationType.UNKNOWN,row.getOperationType());
    }
    @Test void rejectsStaleReviewAndUnchangedValues() {
        when(corrections.findFirstByTransactionIdAndUserOrderByIdDesc(7L,owner)).thenReturn(Optional.of(TransactionCorrection.builder().id(12L).build()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.correct(7L,request())).getStatusCode().value());
        var req=request(); req.setExpectedRevision(12L); req.setAmount(row.getAmount()); req.setTransactionDate(row.getTransactionDate()); req.setFlowType(row.getFlowType());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.correct(7L,req)).getStatusCode().value());
        verify(corrections,never()).save(any()); verify(transactions,never()).save(any());
    }
    @Test void blocksBothSidesOfAnExistingReconciliation() {
        row.setReconciledToId(10L);
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.correct(7L,request())).getStatusCode().value());
        row.setReconciledToId(null);
        when(transactions.findByReconciledToIdAndUser(7L,owner)).thenReturn(Optional.of(Transaction.builder().id(10L).build()));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.correct(7L,request())).getStatusCode().value());
        verify(corrections,never()).save(any());
    }
    @Test void identicalRetriesDoNotWriteAgainButChangedPayloadIsRejected() {
        var req=request();
        var audit=TransactionCorrection.builder().id(22L).expectedRevision(0).reason(req.getReason().trim())
                .afterAmount(req.getAmount()).afterDate(req.getTransactionDate()).afterFlow(req.getFlowType()).build();
        when(corrections.findByTransactionIdAndUserAndRequestId(7L,owner,req.getRequestId())).thenReturn(Optional.of(audit));
        service.correct(7L,req); verify(corrections,never()).save(any()); verify(transactions,never()).save(any());
        req.setReason("Different correction");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.correct(7L,req)).getStatusCode().value());
    }
    @Test void historyUsesOwnedBoundedPagesAndReportsLinkedReadOnlyState() {
        when(corrections.findByTransactionIdAndUserOrderByIdDesc(eq(7L),eq(owner),any())).thenReturn(new PageImpl<>(List.of()));
        assertTrue(service.history(7L,0).isEditable());
        row.setReconciledToId(10L); assertFalse(service.history(7L,0).isEditable());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.history(7L,-1)).getStatusCode().value());
    }
}
