package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.CaptureOrigin;
import com.store.api.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionOriginServiceTest {
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final TransactionOriginService service = new TransactionOriginService(transactions);
    private final User owner = User.builder().id(1L).build();
    @AfterEach void clear() { UserContext.clear(); }

    @Test void requiresAuthenticationAndScopesEveryReadToTheOwner() {
        assertEquals(401,assertThrows(ResponseStatusException.class,()->service.getOrigin(7L)).getStatusCode().value());
        verifyNoInteractions(transactions);
        UserContext.setCurrentUser(owner);
        when(transactions.findByIdAndUser(7L,owner)).thenReturn(Optional.empty());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.getOrigin(7L)).getStatusCode().value());
        verify(transactions).findByIdAndUser(7L,owner);
    }

    @Test void returnsOnlyExplicitFirstCaptureEvidenceWithoutGuessingFromChannelOrRawText() {
        UserContext.setCurrentUser(owner);
        var registered = LocalDateTime.of(2026,1,3,12,0);
        var row = Transaction.builder().id(7L).user(owner).createdAt(registered).captureOrigin(CaptureOrigin.EMAIL)
                .operationReference("OP-123").channel("EXAMPLE_WALLET").build();
        when(transactions.findByIdAndUser(7L,owner)).thenReturn(Optional.of(row));
        var result = service.getOrigin(7L);
        assertEquals(CaptureOrigin.EMAIL,result.getOrigin()); assertEquals("OP-123",result.getOperationReference());
        assertEquals(registered,result.getRegisteredAt());
        row.setCaptureOrigin(CaptureOrigin.PHONE);
        assertEquals(CaptureOrigin.PHONE,service.getOrigin(7L).getOrigin());
        assertNull(service.getOrigin(7L).getOperationReference());
        row.setCaptureOrigin(null);
        assertEquals(CaptureOrigin.UNKNOWN,service.getOrigin(7L).getOrigin());
        assertNull(service.getOrigin(7L).getOperationReference());
        assertEquals(registered,service.getOrigin(7L).getRegisteredAt());
    }
}
