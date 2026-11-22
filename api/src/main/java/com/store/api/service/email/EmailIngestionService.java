package com.store.api.service.email;

import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.dto.email.EmailConnectionTestResponse;
import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.ProcessedEmailMessage;
import com.store.api.model.enums.EmailSyncFailure;
import com.store.api.repository.ProcessedEmailMessageRepository;
import com.store.api.service.transaction.TransactionService;
import com.store.api.service.auth.GoogleOAuthService;
import com.store.api.service.auth.GoogleOAuthService.GmailScan;
import com.store.api.service.email.client.GmailApiClient;
import com.store.api.service.email.client.ImapEmailClient;
import com.store.api.service.email.parser.BankEmailParserDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Domain orchestrator service for email synchronization and transaction persistence.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailIngestionService {

    private final GoogleOAuthService googleOAuthService;
    private final GmailApiClient gmailApiClient;
    private final ImapEmailClient imapEmailClient;
    private final BankEmailParserDispatcher parserDispatcher;
    private final TransactionService transactionService;
    private final ProcessedEmailMessageRepository processedEmailRepository;
    private final Set<Long> activeSyncUsers = ConcurrentHashMap.newKeySet();

    @Value("${mail.sync.batch-size:20}")
    private int batchSize = 20;

    public EmailConnectionTestResponse testConnection() {
        UserContext.requireCurrentUser();
        Optional<String> googleToken = googleOAuthService.getValidAccessToken();
        if (googleToken.isPresent()) {
            return EmailConnectionTestResponse.builder()
                    .status("OK")
                    .message("Conexión activa y autorizada con Google Gmail API (OAuth2)")
                    .build();
        }
        return EmailConnectionTestResponse.builder().status("ERROR")
                .message("Conecta tu cuenta de Google para consultar el correo.").build();
    }

    public boolean hasActiveConnection() {
        return googleOAuthService.getValidAccessToken().isPresent();
    }

    public EmailSyncResponse syncEmails() {
        return syncEmailsIfIdle().orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Ya hay una sincronización de correo en curso. Espera a que termine."));
    }

    public Optional<EmailSyncResponse> syncEmailsIfIdle() {
        Long userId = UserContext.requireCurrentUser().getId();
        if (!activeSyncUsers.add(userId)) return Optional.empty();

        try {
            GmailScan saved = googleOAuthService.getGmailScan().orElseThrow(() ->
                    new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.CONFLICT, "Conecta Gmail para sincronizar el correo."));
            if (saved.retryAt() != null && Instant.now().isBefore(saved.retryAt())) {
                return Optional.of(response(new SyncProgress(), "PAUSED", "Gmail pidió una pausa temporal."));
            }
            Optional<String> googleToken = fetchTokenWithRetry();

            if (googleToken.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Conecta de nuevo tu cuenta de Google para sincronizar el correo.");
            }

            SyncProgress progress = new SyncProgress();
            Long lastSyncedInternalDate = googleOAuthService.getLastSyncedInternalDate().orElse(null);
            ScanBatch batch = new ScanBatch(saved, progress);
            try {
                visitEmailsWithRetry(googleToken.get(), lastSyncedInternalDate, batch);
            } catch (GmailApiClient.InvalidPageToken invalid) {
                // Replay the same window. Existing per-user markers skip completed messages.
                batch.savePage(null, false);
            } catch (EmailSyncException ex) {
                if (ex.getFailure() == EmailSyncFailure.RATE_LIMITED) batch.pause();
                throw ex;
            }
            if (progress.errors > 0) {
                googleOAuthService.recordSyncResult(false, EmailSyncFailure.PROCESSING);
                return Optional.of(response(progress, "PARTIAL", "Algunos correos siguen pendientes."));
            }
            if (batch.complete) {
                googleOAuthService.recordSyncResult(true, null);
                return Optional.of(response(progress, "SUCCESS", "Correo sincronizado."));
            }
            return Optional.of(response(progress, "CONTINUING", "Quedan correos por revisar."));
        } catch (RuntimeException ex) {
            var failure = ex instanceof org.springframework.web.server.ResponseStatusException status
                    && status.getStatusCode().value() == 409 ? EmailSyncFailure.AUTH_REQUIRED : EmailSyncException.classify(ex);
            googleOAuthService.recordSyncResult(false, failure);
            throw ex;
        } finally {
            activeSyncUsers.remove(userId);
        }
    }

    private Optional<String> fetchTokenWithRetry() {
        try {
            return googleOAuthService.getValidAccessToken();
        } catch (EmailSyncException ex) {
            if (ex.getFailure() == EmailSyncFailure.TEMPORARY) {
                log.info("Retrying Google token refresh after temporary transport failure");
                return googleOAuthService.getValidAccessToken();
            }
            throw ex;
        }
    }

    private EmailSyncResponse response(SyncProgress progress, String status, String message) {
        return EmailSyncResponse.builder().status(status).scannedCount(progress.scanned)
                .processedInBatch(progress.scanned).savedCount(progress.saved)
                .transactions(progress.transactions).message(message).build();
    }

    private void visitEmails(String token, Long cursor, ScanBatch batch) {
        gmailApiClient.visitFinancialEmails(token, batch.state.pageSize(), cursor,
                id -> processedEmailRepository.existsById(processedKey(id)),
                msg -> processMessage(msg, batch.progress),
                new GmailApiClient.Scan(batch.state.windowEnd(), batch.state.pageToken(), batch::pageComplete));
    }

    private void visitEmailsWithRetry(String initialToken, Long lastSyncedInternalDate, ScanBatch batch) {
        try {
            visitEmails(initialToken, lastSyncedInternalDate, batch);
        } catch (EmailSyncException ex) {
            if (batch.progress.scanned > 0 || batch.pages >= 5) throw ex;
            if (ex.getFailure() == EmailSyncFailure.TEMPORARY) {
                log.info("Retrying Gmail synchronization after temporary transport failure");
                visitEmails(initialToken, lastSyncedInternalDate, batch);
                return;
            }
            if (ex.getFailure() == EmailSyncFailure.AUTH_REQUIRED) {
                Optional<String> refreshed = googleOAuthService.refreshAccessToken();
                if (refreshed.isPresent()) {
                    log.info("Retrying Gmail synchronization with refreshed access token");
                    visitEmails(refreshed.get(), lastSyncedInternalDate, batch);
                    return;
                }
            }
            throw ex;
        }
    }

    private final class ScanBatch {
        private GmailScan state;
        private final SyncProgress progress;
        private int pages;
        private boolean complete;

        private ScanBatch(GmailScan state, SyncProgress progress) {
            this.state = state;
            this.progress = progress;
            if (state.windowEnd() == null) {
                long cutoff = Instant.now().getEpochSecond() * 1000;
                save(new GmailScan(state.tokenId(), cutoff, null, Math.clamp(batchSize, 1, 30), null, 0), null);
            }
        }

        private boolean pageComplete(String next) {
            if (progress.errors > 0) return false;
            savePage(next, next == null);
            return !complete && progress.scanned == 0 && ++pages < 5;
        }

        private void savePage(String next, boolean finished) {
            Long completedThrough = finished ? state.windowEnd() : null;
            GmailScan updated = finished
                    ? new GmailScan(state.tokenId(), null, null, null, null, 0)
                    : new GmailScan(state.tokenId(), state.windowEnd(), next, state.pageSize(), null, state.rateLimitCount());
            save(updated, completedThrough);
            complete = finished;
        }

        private void pause() {
            int count = Math.min(state.rateLimitCount() + 1, 5);
            Instant retryAt = Instant.now().plusSeconds(Math.min(3600, 300L << (count - 1)));
            save(new GmailScan(state.tokenId(), state.windowEnd(), state.pageToken(), state.pageSize(), retryAt, count), null);
        }

        private void save(GmailScan next, Long completedThrough) {
            googleOAuthService.saveGmailScan(state, next, completedThrough);
            state = next;
        }
    }

    private static class SyncProgress {
        int scanned;
        int saved;
        int errors;
        final List<TransactionResponse> transactions = new ArrayList<>();
    }

    private void processMessage(EmailMessageDto msg, SyncProgress progress) {
            progress.scanned++;
            try {
                Optional<ParsedEmailTransaction> parsedOpt = parserDispatcher.dispatchAndParse(
                        msg.getFrom(),
                        msg.getSubject(),
                        msg.getBody(),
                        msg.getSentDate()
                );

                if (parsedOpt.isPresent()) {
                    ParsedEmailTransaction parsed = parsedOpt.get();
                    TransactionSyncRequest req = new TransactionSyncRequest();
                    req.setAmount(parsed.getAmount());
                    req.setFlowType(parsed.getFlowType());
                    req.setOperationType(parsed.getOperationType());
                    req.setContactName(parsed.getMerchantName());
                    req.setChannel(parsed.getChannel());
                    req.setCardLast4(parsed.getCardLast4());
                    req.setTransactionDate(parsed.getTransactionDate());
                    req.setTransactionHash(parsed.getTransactionHash());
                    String rawBody = parsed.getRawBody();
                    req.setRawNotificationText(rawBody == null ? null : rawBody.substring(0,
                            Math.min(rawBody.length(), TransactionSyncRequest.MAX_RAW_TEXT_LENGTH)));

                    TransactionResponse saved = transactionService.processEmailAndSave(req, parsed.getReceiptReference());
                    progress.transactions.add(saved);
                    progress.saved++;
                }

                if (msg.getMessageId() != null && !processedEmailRepository.existsById(processedKey(msg.getMessageId()))) {
                    processedEmailRepository.save(ProcessedEmailMessage.builder()
                            .messageId(processedKey(msg.getMessageId()))
                            .user(UserContext.requireCurrentUser())
                            .internalDateMs(msg.getInternalDateMs())
                            .build());
                }
            } catch (Exception ex) {
                progress.errors++;
                log.warn("An email message could not be processed");
            }
    }

    private String processedKey(String messageId) {
        String scope = UserContext.requireCurrentUser().getId() + ":" + messageId;
        try {
            return "v2:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
