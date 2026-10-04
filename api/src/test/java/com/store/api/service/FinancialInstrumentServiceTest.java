package com.store.api.service;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.FinancialInstrumentRequest;
import com.store.api.model.entity.FinancialInstrument;
import com.store.api.model.entity.User;
import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import com.store.api.repository.FinancialInstrumentRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinancialInstrumentServiceTest {
    private final FinancialInstrumentRepository repository = mock(FinancialInstrumentRepository.class);
    private final FinancialInstrumentService service = new FinancialInstrumentService(repository);
    private final User user = User.builder().id(7L).email("owner@example.test").build();

    @BeforeEach void setup() {
        UserContext.setCurrentUser(user);
        when(repository.save(any())).thenAnswer(invocation -> {
            FinancialInstrument item = invocation.getArgument(0);
            if (item.getId() == null) item.setId(10L);
            return item;
        });
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void anonymousCannotListCreateOrEdit() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.list(null)).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.create(request())).getStatusCode().value());
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.update(1L, request())).getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test void createsTrimmedProductForCurrentUserWithFixedCurrency() {
        var result = service.create(request());
        assertEquals("Main account", result.getAlias());
        assertEquals("PEN", result.getCurrency());
        assertTrue(result.isActive());
        verify(repository).save(argThat(item -> item.getUser() == user && item.getType() == InstrumentType.BANK_ACCOUNT));
    }

    @Test void listUsesOwnerAndActiveFilter() {
        FinancialInstrument account = account();
        FinancialInstrument archived = account(); archived.setActive(false);
        when(repository.findByUserOrderByAliasAscIdAsc(user)).thenReturn(List.of(account, archived));
        assertEquals(2, service.list(null).size());
        assertEquals(1, service.list(true).size());
        assertEquals(1, service.list(false).size());
    }

    @Test void missingOrForeignProductCannotBeEdited() {
        when(repository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.update(99L, request())).getStatusCode().value());
        verify(repository, never()).save(any());
    }

    @Test void otherBankRequiresInstitutionName() {
        var request = request(); request.setBank(Bank.OTHER); request.setInstitutionName(" ");
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(request)).getStatusCode().value());
        request.setInstitutionName(" Cooperative ");
        assertEquals("Cooperative", service.create(request).getInstitutionName());
    }

    @Test void debitLinksToOwnedActiveAccountAndDoesNotRequireOne() {
        var request = request(); request.setType(InstrumentType.DEBIT_CARD);
        assertNull(service.create(request).getLinkedAccountId());
        request.setLinkedAccountId(20L);
        when(repository.findByIdAndUser(20L, user)).thenReturn(Optional.of(account()));
        assertEquals(20L, service.create(request).getLinkedAccountId());
    }

    @Test void rejectsForeignArchivedWrongBankAndWrongTypeLinks() {
        var request = request(); request.setType(InstrumentType.DEBIT_CARD); request.setLinkedAccountId(20L);
        when(repository.findByIdAndUser(20L, user)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.create(request)).getStatusCode().value());
        FinancialInstrument target = account();
        when(repository.findByIdAndUser(20L, user)).thenReturn(Optional.of(target));
        target.setActive(false);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.create(request)).getStatusCode().value());
        target.setActive(true); target.setBank(Bank.BBVA);
        assertThrows(ResponseStatusException.class, () -> service.create(request));
        target.setBank(Bank.BCP); target.setType(InstrumentType.CREDIT_CARD);
        assertThrows(ResponseStatusException.class, () -> service.create(request));
        target.setType(InstrumentType.BANK_ACCOUNT); request.setType(InstrumentType.CREDIT_CARD);
        assertThrows(ResponseStatusException.class, () -> service.create(request));
    }

    @Test void archivesAndReactivatesAccountWithoutDiscardingLinks() {
        FinancialInstrument account = account();
        when(repository.findByIdAndUser(20L, user)).thenReturn(Optional.of(account));
        when(repository.existsByLinkedAccount(account)).thenReturn(true);
        var request = request(); request.setActive(false);
        assertFalse(service.update(20L, request).isActive());
        request.setActive(true);
        assertTrue(service.update(20L, request).isActive());
        request.setBank(Bank.BBVA);
        assertThrows(ResponseStatusException.class, () -> service.update(20L, request));
    }

    @Test void archivingDebitPreservesLinkToArchivedAccountButReactivationNeedsActiveAccount() {
        FinancialInstrument account = account(); account.setActive(false);
        FinancialInstrument card = FinancialInstrument.builder().id(30L).user(user).type(InstrumentType.DEBIT_CARD)
                .bank(Bank.BCP).alias("Debit").linkedAccount(account).active(true).build();
        when(repository.findByIdAndUser(20L, user)).thenReturn(Optional.of(account));
        when(repository.findByIdAndUser(30L, user)).thenReturn(Optional.of(card));
        var request = request(); request.setType(InstrumentType.DEBIT_CARD); request.setLinkedAccountId(20L); request.setActive(false);
        assertEquals(20L, service.update(30L, request).getLinkedAccountId());
        request.setActive(true);
        assertThrows(ResponseStatusException.class, () -> service.update(30L, request));
        request.setLinkedAccountId(null);
        assertTrue(service.update(30L, request).isActive());
    }

    @Test void validatesAliasTypeBankAndExactFourDigits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = request();
            assertTrue(validator.validate(request).isEmpty());
            request.setLastFour("123");
            assertFalse(validator.validate(request).isEmpty());
            request.setLastFour("12ab");
            assertFalse(validator.validate(request).isEmpty());
            request.setLastFour("1234"); request.setAlias(" "); request.setBank(null); request.setType(null);
            assertTrue(validator.validate(request).size() >= 3);
        }
    }

    private FinancialInstrument account() {
        return FinancialInstrument.builder().id(20L).user(user).type(InstrumentType.BANK_ACCOUNT).alias("Main account").bank(Bank.BCP).build();
    }
    private FinancialInstrumentRequest request() {
        var request = new FinancialInstrumentRequest();
        request.setAlias(" Main account "); request.setType(InstrumentType.BANK_ACCOUNT); request.setBank(Bank.BCP);
        return request;
    }
}
