package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.instrument.FinancialInstrumentResponse;
import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.entity.FinancialInstrument;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.Bank;
import com.store.api.model.enums.InstrumentType;
import com.store.api.repository.FinancialInstrumentRepository;
import com.store.api.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class InstrumentAssignmentService {
    private final TransactionRepository transactions;
    private final FinancialInstrumentRepository instruments;
    private static final Map<String, InstrumentType> CHANNEL_TYPES = Map.of(
            "TARJETA_CREDITO", InstrumentType.CREDIT_CARD,
            "TARJETA_DEBITO", InstrumentType.DEBIT_CARD);

    @Transactional
    public TransactionResponse assign(Long transactionId, Long instrumentId) {
        User user = UserContext.requireCurrentUser();
        Transaction transaction = requireTransaction(transactionId, user);
        FinancialInstrument instrument = null;
        if (instrumentId != null) {
            instrument = instruments.findByIdAndUser(instrumentId, user)
                    .orElseThrow(() -> missing("Financial instrument not found"));
            if (!instrument.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archived products cannot receive new assignments");
            }
        }
        transaction.setFinancialInstrument(instrument);
        return TransactionService.mapToResponse(transactions.save(transaction));
    }

    @Transactional(readOnly = true)
    public List<FinancialInstrumentResponse> suggestions(Long transactionId) {
        User user = UserContext.requireCurrentUser();
        Transaction transaction = requireTransaction(transactionId, user);
        String channel = transaction.getChannel();
        String lastFour = transaction.getCardLast4();
        if (channel == null || lastFour == null || !lastFour.matches("[0-9]{4}")) return List.of();
        Bank bank = null;
        InstrumentType type = null;
        for (Bank candidate : List.of(Bank.BCP, Bank.INTERBANK, Bank.BBVA)) {
            for (Map.Entry<String, InstrumentType> entry : CHANNEL_TYPES.entrySet()) {
                if (channel.equals(entry.getKey() + "_" + candidate.name())) {
                    bank = candidate;
                    type = entry.getValue();
                }
            }
        }
        if ("BCP_TRANSFERENCIA".equals(channel)) {
            bank = Bank.BCP;
            type = InstrumentType.BANK_ACCOUNT;
        }
        if (bank == null || type == null) return List.of();
        Bank matchedBank = bank;
        InstrumentType matchedType = type;
        return instruments.findByUserOrderByAliasAscIdAsc(user).stream()
                .filter(item -> item.isActive() && item.getBank() == matchedBank && item.getType() == matchedType
                        && lastFour.equals(item.getLastFour()))
                .map(FinancialInstrumentResponse::from).toList();
    }

    private Transaction requireTransaction(Long id, User user) {
        return transactions.findByIdAndUser(id, user).orElseThrow(() -> missing("Transaction not found"));
    }
    private static ResponseStatusException missing(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
