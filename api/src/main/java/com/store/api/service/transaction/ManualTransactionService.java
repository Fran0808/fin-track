package com.store.api.service.transaction;

import com.store.api.config.security.UserContext;
import com.store.api.model.dto.transaction.*;
import com.store.api.model.entity.Transaction;
import com.store.api.model.entity.User;
import com.store.api.model.enums.CaptureOrigin;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Validated
public class ManualTransactionService {
    private static final int LIMIT = 50;
    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final FinancialInstrumentRepository instruments;
    private final byte[] signingKey;

    public ManualTransactionService(TransactionRepository transactions, CategoryRepository categories,
            FinancialInstrumentRepository instruments, @Value("${jwt.secret}") String secret) {
        this.transactions = transactions;
        this.categories = categories;
        this.instruments = instruments;
        this.signingKey = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public TransactionResponse create(@NotNull @Valid ManualTransactionRequest request) {
        User user = UserContext.requireCurrentUser();
        if (request.getFlowType() != FlowType.EXPENSE && request.getFlowType() != FlowType.INCOME) {
            throw bad("Selecciona gasto o entrada. Las transferencias propias no deben registrarse como gastos.");
        }
        String hash = "manual:" + UUID.fromString(request.getRequestId());
        String fingerprint = fingerprint(request);
        transactions.lockTransactionHash(user.getId(), hash);
        var existing = transactions.findByTransactionHashAndUser(hash, user);
        if (existing.isPresent()) {
            if (existing.get().getCaptureOrigin() != CaptureOrigin.MANUAL
                    || !fingerprint.equals(existing.get().getManualRequestFingerprint())) {
                throw conflict("Este envío ya se guardó con otros datos. Revisa el listado antes de registrar otro movimiento.");
            }
            return TransactionService.mapToResponse(existing.get());
        }
        var transaction = Transaction.builder().user(user).amount(request.getAmount())
                .flowType(request.getFlowType()).contactName(request.getContactName().trim())
                .transactionDate(request.getTransactionDate()).channel(request.getPaymentMethod().name())
                .captureOrigin(CaptureOrigin.MANUAL).transactionHash(hash).manualRequestFingerprint(fingerprint).build();
        if (request.getCategoryId() != null) {
            var category = categories.findByIdAndUser(request.getCategoryId(), user)
                    .orElseThrow(() -> bad("Selecciona una categoría activa de tu catálogo."));
            if (!category.isActive()) throw bad("Selecciona una categoría activa de tu catálogo.");
            transaction.setCategory(category.getName());
        }
        if (request.getFinancialInstrumentId() != null) {
            var product = instruments.findByIdAndUser(request.getFinancialInstrumentId(), user)
                    .orElseThrow(() -> bad("Selecciona una tarjeta o cuenta activa de tu catálogo."));
            if (!product.isActive()) throw bad("Selecciona una tarjeta o cuenta activa de tu catálogo.");
            transaction.setFinancialInstrument(product);
        }
        return TransactionService.mapToResponse(transactions.save(transaction));
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ManualReconciliationPreview preview(Long manualId) {
        User user = UserContext.requireCurrentUser();
        Transaction manual = requireManual(owned(manualId, user));
        if (manual.getReconciledToId() != null) throw conflict("Este registro manual ya está conciliado.");
        var date = manual.getTransactionDate().toLocalDate();
        var candidates = transactions.findManualReconciliationCandidates(user, manual.getAmount(), manual.getFlowType(),
                date.minusDays(3).atStartOfDay(), date.plusDays(4).atStartOfDay(),
                PageRequest.of(0, LIMIT + 1, Sort.by(Sort.Direction.DESC, "transactionDate", "id")));
        long expiresAt = Instant.now().getEpochSecond() + 600;
        return new ManualReconciliationPreview(TransactionService.mapToResponse(manual), candidates.stream().limit(LIMIT)
                .map(captured -> new ManualReconciliationPreview.Candidate(TransactionService.mapToResponse(captured),
                        sign(user, manual, captured, expiresAt))).toList(), LIMIT, candidates.size() > LIMIT, expiresAt);
    }

    @Transactional
    public TransactionResponse reconcile(Long manualId, @NotNull @Valid ManualReconciliationRequest request) {
        User user = UserContext.requireCurrentUser();
        var pair = lockedPair(manualId, request.getCapturedId(), user);
        Transaction manual = requireManual(pair.get(0));
        Transaction captured = pair.get(1);
        if (manual.getReconciledToId() != null) {
            if (manual.getReconciledToId().equals(captured.getId())) return TransactionService.mapToResponse(captured);
            throw conflict("El registro manual ya está vinculado a otra captura.");
        }
        long now = Instant.now().getEpochSecond();
        if (request.getExpiresAt() <= now || request.getExpiresAt() > now + 600 || !eligible(manual, captured)
                || !MessageDigest.isEqual(sign(user, manual, captured, request.getExpiresAt()).getBytes(StandardCharsets.UTF_8),
                        request.getPreviewToken().getBytes(StandardCharsets.UTF_8))) {
            throw conflict("La revisión venció o cambió un movimiento. Actualiza las coincidencias y vuelve a revisar.");
        }
        if (transactions.findByReconciledToIdAndUser(captured.getId(), user).isPresent()) {
            throw conflict("Esta captura ya está vinculada a otro registro manual.");
        }
        manual.setReconciledToId(captured.getId());
        manual.setReconciledAt(LocalDateTime.now());
        manual.setReconciliationRevision(manual.getReconciliationRevision() + 1);
        // Preserve both financial records and the capture's existing classification and product.
        transactions.save(manual);
        return TransactionService.mapToResponse(captured);
    }

    @Transactional(readOnly = true)
    public ManualReconciliationStatus status(Long id) {
        User user = UserContext.requireCurrentUser();
        Transaction selected = owned(id, user);
        Transaction manual = selected.getCaptureOrigin() == CaptureOrigin.MANUAL ? selected
                : transactions.findByReconciledToIdAndUser(id, user).orElse(null);
        Transaction captured = manual == null || manual.getReconciledToId() == null ? null : owned(manual.getReconciledToId(), user);
        return new ManualReconciliationStatus(manual == null ? null : TransactionService.mapToResponse(manual),
                captured == null ? null : TransactionService.mapToResponse(captured), manual == null ? null : manual.getReconciledAt(), manual == null ? 0 : manual.getReconciliationRevision());
    }

    @Transactional
    public TransactionResponse undo(Long manualId, Long capturedId, Integer revision) {
        User user = UserContext.requireCurrentUser();
        var pair = lockedPair(manualId, capturedId, user);
        Transaction manual = requireManual(pair.get(0));
        if (revision == null || revision < 1) throw bad("Vuelve a consultar la conciliación antes de deshacerla.");
        if (manual.getReconciledToId() != null && (!manual.getReconciledToId().equals(capturedId) || manual.getReconciliationRevision() != revision)) {
            throw conflict("La conciliación cambió. Vuelve a consultar el detalle.");
        }
        if (manual.getReconciledToId() != null) manual.setReconciliationRevision(manual.getReconciliationRevision() + 1);
        manual.setReconciledToId(null);
        manual.setReconciledAt(null);
        transactions.save(manual);
        return TransactionService.mapToResponse(pair.get(1));
    }

    private List<Transaction> lockedPair(Long manualId, Long capturedId, User user) {
        if (manualId == null || capturedId == null || manualId.equals(capturedId)) throw bad("Selecciona dos movimientos distintos.");
        var rows = transactions.lockHistorySelection(user, List.of(manualId, capturedId));
        if (rows.size() != 2) throw missing();
        return List.of(rows.stream().filter(t -> t.getId().equals(manualId)).findFirst().orElseThrow(ManualTransactionService::missing),
                rows.stream().filter(t -> t.getId().equals(capturedId)).findFirst().orElseThrow(ManualTransactionService::missing));
    }

    private boolean eligible(Transaction manual, Transaction captured) {
        return (captured.getCaptureOrigin() == CaptureOrigin.EMAIL || captured.getCaptureOrigin() == CaptureOrigin.PHONE)
                && captured.getReconciledToId() == null && manual.getAmount().compareTo(captured.getAmount()) == 0
                && manual.getFlowType() == captured.getFlowType()
                && Math.abs(ChronoUnit.DAYS.between(manual.getTransactionDate().toLocalDate(), captured.getTransactionDate().toLocalDate())) <= 3;
    }

    private Transaction owned(Long id, User user) {
        return transactions.findByIdAndUser(id, user).orElseThrow(ManualTransactionService::missing);
    }

    private Transaction requireManual(Transaction transaction) {
        if (transaction.getCaptureOrigin() != CaptureOrigin.MANUAL) throw bad("Selecciona un registro manual.");
        return transaction;
    }

    private String fingerprint(ManualTransactionRequest request) {
        return digest(data -> {
            data.writeUTF("fintrack-manual-create-v1"); data.writeUTF(request.getAmount().stripTrailingZeros().toPlainString());
            data.writeUTF(request.getFlowType().name()); data.writeUTF(request.getContactName().trim());
            data.writeUTF(request.getTransactionDate().toString()); data.writeUTF(request.getPaymentMethod().name());
            data.writeLong(request.getCategoryId() == null ? 0 : request.getCategoryId());
            data.writeLong(request.getFinancialInstrumentId() == null ? 0 : request.getFinancialInstrumentId());
        }, false);
    }

    private String sign(User user, Transaction manual, Transaction captured, long expiresAt) {
        return digest(data -> {
            data.writeUTF("fintrack-manual-reconciliation-v1"); data.writeLong(user.getId()); data.writeLong(expiresAt); data.writeInt(manual.getReconciliationRevision());
            for (Transaction item : List.of(manual, captured)) {
                data.writeLong(item.getId()); data.writeUTF(item.getAmount().stripTrailingZeros().toPlainString());
                data.writeUTF(item.getFlowType().name()); data.writeUTF(item.getTransactionDate().toString());
                data.writeUTF(item.getContactName()); data.writeUTF(item.getChannel()); data.writeUTF(item.getCaptureOrigin().name());
                data.writeUTF(Objects.toString(item.getCategory(), ""));
                data.writeLong(item.getFinancialInstrument() == null ? 0 : item.getFinancialInstrument().getId());
            }
        }, true);
    }

    private interface Encoder { void write(DataOutputStream data) throws Exception; }
    private String digest(Encoder encoder, boolean signed) {
        try {
            var bytes = new ByteArrayOutputStream();
            encoder.write(new DataOutputStream(bytes));
            byte[] result;
            if (signed) {
                Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
                result = mac.doFinal(bytes.toByteArray());
            } else result = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(result);
        } catch (Exception error) { throw new IllegalStateException("Manual transaction verification unavailable"); }
    }

    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado."); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
