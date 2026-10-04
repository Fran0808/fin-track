package com.store.api.service;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.FinancialInstrumentRequest;
import com.store.api.model.dto.FinancialInstrumentResponse;
import com.store.api.model.entity.FinancialInstrument;
import com.store.api.model.entity.User;
import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import com.store.api.repository.FinancialInstrumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service @RequiredArgsConstructor
public class FinancialInstrumentService {
    private final FinancialInstrumentRepository repository;

    @Transactional(readOnly = true)
    public List<FinancialInstrumentResponse> list(Boolean active) {
        return repository.findByUserOrderByAliasAscIdAsc(UserContext.requireCurrentUser()).stream()
                .filter(item -> active == null || item.isActive() == active)
                .map(FinancialInstrumentResponse::from).toList();
    }

    @Transactional
    public FinancialInstrumentResponse create(FinancialInstrumentRequest request) {
        User user = UserContext.requireCurrentUser();
        FinancialInstrument item = FinancialInstrument.builder().user(user).build();
        apply(item, request, user);
        return FinancialInstrumentResponse.from(repository.save(item));
    }

    @Transactional
    public FinancialInstrumentResponse update(Long id, FinancialInstrumentRequest request) {
        User user = UserContext.requireCurrentUser();
        FinancialInstrument item = requireOwned(id, user);
        // Existing debit links must keep pointing at a compatible bank account.
        boolean bankChanged = request.getBank() != item.getBank()
                || (item.getBank() == Bank.OTHER
                && !normalized(request.getInstitutionName()).equals(normalized(item.getInstitutionName())));
        if (repository.existsByLinkedAccount(item)
                && (request.getType() != InstrumentType.BANK_ACCOUNT || bankChanged)) {
            throw badRequest("Unlink debit cards before changing the account type or bank");
        }
        apply(item, request, user);
        return FinancialInstrumentResponse.from(repository.save(item));
    }

    @Transactional(readOnly = true)
    public FinancialInstrument requireOwned(Long id, User user) {
        return repository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Financial instrument not found"));
    }

    private void apply(FinancialInstrument item, FinancialInstrumentRequest request, User user) {
        String institution = request.getBank() == Bank.OTHER ? normalized(request.getInstitutionName()) : null;
        if (request.getBank() == Bank.OTHER && (institution == null || institution.isBlank())) {
            throw badRequest("Institution name is required for Other");
        }
        FinancialInstrument account = null;
        if (request.getLinkedAccountId() != null) {
            if (request.getType() != InstrumentType.DEBIT_CARD) throw badRequest("Only debit cards can link to an account");
            if (request.getLinkedAccountId().equals(item.getId())) throw badRequest("A product cannot link to itself");
            account = requireOwned(request.getLinkedAccountId(), user);
            boolean sameBank = account.getBank() == request.getBank()
                    && (request.getBank() != Bank.OTHER || normalized(account.getInstitutionName()).equals(institution));
            if (account.getType() != InstrumentType.BANK_ACCOUNT || !sameBank) {
                throw badRequest("Select a bank account from the same bank");
            }
            boolean existingLink = item.getLinkedAccount() != null
                    && account.getId().equals(item.getLinkedAccount().getId());
            boolean reactivating = !item.isActive() && request.getActive();
            if (!account.isActive() && (!existingLink || reactivating)) {
                throw badRequest("Select an active account");
            }
        }
        item.setType(request.getType());
        item.setAlias(request.getAlias().trim());
        item.setBank(request.getBank());
        item.setInstitutionName(institution);
        item.setLastFour(request.getLastFour());
        item.setActive(request.getActive());
        item.setLinkedAccount(account);
    }

    private static String normalized(String value) { return value == null ? "" : value.trim(); }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
