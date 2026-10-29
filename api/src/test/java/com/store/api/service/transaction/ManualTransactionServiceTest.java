package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.*;
import com.store.api.model.entity.*;
import com.store.api.model.enums.*;
import com.store.api.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManualTransactionServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final FinancialInstrumentRepository products = mock(FinancialInstrumentRepository.class);
    private final ManualTransactionService service = new ManualTransactionService(transactions,categories,products,"synthetic-signing-key-at-least-32-bytes");
    private final User owner = User.builder().id(1L).build();
    @AfterEach void clear() { UserContext.clear(); }

    static ManualTransactionRequest request() {
        var req = new ManualTransactionRequest(); req.setRequestId("01234567-89ab-cdef-0123-456789abcdef");
        req.setAmount(new BigDecimal("12.34")); req.setFlowType(FlowType.EXPENSE); req.setContactName(" Synthetic store ");
        req.setTransactionDate(LocalDateTime.of(2026,1,31,12,0)); req.setPaymentMethod(ManualPaymentMethod.CASH); return req;
    }

    @Test void rejectsAnonymousBeforeReadingOrWritingAnyAccount() {
        assertEquals(401,assertThrows(ResponseStatusException.class,()->service.create(request())).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->service.preview(1L));
        assertThrows(ResponseStatusException.class,()->service.status(1L));
        assertThrows(ResponseStatusException.class,()->service.undo(1L,2L,1));
        var apply = new ManualReconciliationRequest(); apply.setCapturedId(2L);
        assertThrows(ResponseStatusException.class,()->service.reconcile(1L,apply));
        verifyNoInteractions(transactions,categories,products);
    }

    @Test void createsManualEvidenceWithoutPretendingToBeAPhoneCaptureOrApplyingRules() {
        UserContext.setCurrentUser(owner);
        when(transactions.save(any())).thenAnswer(call->{Transaction t=call.getArgument(0);t.setId(7L);return t;});
        var result = service.create(request());
        assertEquals(CaptureOrigin.MANUAL,result.getCaptureOrigin()); assertEquals("CASH",result.getChannel());
        assertEquals(new BigDecimal("12.34"),result.getAmount()); assertEquals("Synthetic store",result.getContactName());
        var saved = org.mockito.ArgumentCaptor.forClass(Transaction.class); verify(transactions).save(saved.capture());
        assertEquals(owner,saved.getValue().getUser()); assertNull(saved.getValue().getOperationReference());
        assertEquals(64,saved.getValue().getManualRequestFingerprint().length());
        verify(transactions).lockTransactionHash(1L,"manual:01234567-89ab-cdef-0123-456789abcdef");
        verifyNoInteractions(categories,products);
    }

    @Test void rejectsTransfersAndForeignOrArchivedCatalogEntries() {
        UserContext.setCurrentUser(owner); var req=request(); req.setFlowType(FlowType.INTERNAL_TRANSFER);
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(req)).getStatusCode().value());
        req.setFlowType(FlowType.EXPENSE); req.setCategoryId(2L);
        assertThrows(ResponseStatusException.class,()->service.create(req));
        when(categories.findByIdAndUser(2L,owner)).thenReturn(Optional.of(Category.builder().id(2L).active(false).build()));
        assertThrows(ResponseStatusException.class,()->service.create(req));
        req.setCategoryId(null); req.setFinancialInstrumentId(3L);
        assertThrows(ResponseStatusException.class,()->service.create(req));
        when(products.findByIdAndUser(3L,owner)).thenReturn(Optional.of(FinancialInstrument.builder().id(3L).active(false).build()));
        assertThrows(ResponseStatusException.class,()->service.create(req)); verify(transactions,never()).save(any());
    }

    @Test void reviewIsBoundedAndEveryCandidateHasAnOwnedExpiringSignature() {
        UserContext.setCurrentUser(owner);
        var entry=Transaction.builder().id(1L).user(owner).amount(new BigDecimal("12.34")).flowType(FlowType.EXPENSE)
                .captureOrigin(CaptureOrigin.MANUAL).contactName("Manual").channel("CASH")
                .transactionDate(LocalDateTime.of(2026,1,31,12,0)).build();
        when(transactions.findByIdAndUser(1L,owner)).thenReturn(Optional.of(entry));
        var rows=java.util.stream.LongStream.range(2,53).mapToObj(id->Transaction.builder().id(id).user(owner)
                .amount(entry.getAmount()).flowType(entry.getFlowType()).captureOrigin(CaptureOrigin.EMAIL)
                .contactName("Synthetic receipt").channel("TEST").transactionDate(entry.getTransactionDate()).build()).toList();
        when(transactions.findManualReconciliationCandidates(eq(owner),any(),any(),any(),any(),any())).thenReturn(rows);
        var result=service.preview(1L);
        assertEquals(50,result.getCandidates().size());assertTrue(result.isHasMore());
        assertTrue(result.getExpiresAt()>java.time.Instant.now().getEpochSecond());
        assertTrue(result.getCandidates().stream().allMatch(item->item.getPreviewToken().matches("[a-f0-9]{64}")));
        assertNotEquals(result.getCandidates().get(0).getPreviewToken(),result.getCandidates().get(1).getPreviewToken());
        verify(transactions,never()).save(any());
    }

    @Test void missingAndForeignManualsHaveTheSameOwnedLookupFailure() {
        UserContext.setCurrentUser(owner);
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.preview(99L)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.status(99L)).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.undo(99L,100L,1)).getStatusCode().value());
        verify(transactions,times(2)).findByIdAndUser(99L,owner);
    }
}
