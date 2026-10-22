package com.store.api.service.email;

import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.dto.transaction.TransactionSyncRequest;
import com.store.api.model.dto.email.EmailConnectionTestResponse;
import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.ProcessedEmailMessage;
import com.store.api.repository.ProcessedEmailMessageRepository;
import com.store.api.service.transaction.TransactionService;
import com.store.api.service.auth.GoogleOAuthService;
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

    @Value("${mail.sync.batch-size:20}")
    private int batchSize;

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
        UserContext.requireCurrentUser();

        try {
            Optional<String> googleToken = googleOAuthService.getValidAccessToken();

        if (googleToken.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Conecta de nuevo tu cuenta de Google para sincronizar el correo.");
        }

            SyncProgress progress = new SyncProgress();
            Long lastSyncedInternalDate = googleOAuthService.getLastSyncedInternalDate().orElse(null);
            gmailApiClient.visitFinancialEmails(
                    googleToken.get(),
                    batchSize,
                    lastSyncedInternalDate,
                    id -> processedEmailRepository.existsById(processedKey(id)),
                    msg -> processMessage(msg, progress)
            );
            if (progress.errors == 0 && progress.maxDate > 0) googleOAuthService.updateLastSyncedInternalDate(progress.maxDate);
            googleOAuthService.recordSyncResult(progress.errors == 0);
            return EmailSyncResponse.builder().status(progress.errors == 0 ? "SUCCESS" : "PARTIAL")
                    .scannedCount(progress.scanned).processedInBatch(progress.scanned).savedCount(progress.saved)
                    .transactions(progress.transactions).message(progress.errors == 0
                            ? "Emails synchronized successfully" : "Some email messages could not be processed").build();
        } catch (RuntimeException ex) {
            googleOAuthService.recordSyncResult(false);
            throw ex;
        }
    }

    private static class SyncProgress {
        int scanned;
        int saved;
        int errors;
        long maxDate;
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
                    req.setContactName(parsed.getMerchantName());
                    req.setChannel(parsed.getChannel());
                    req.setCardLast4(parsed.getCardLast4());
                    req.setTransactionDate(parsed.getTransactionDate());
                    req.setTransactionHash(parsed.getTransactionHash());
                    String rawBody = parsed.getRawBody();
                    req.setRawNotificationText(rawBody == null ? null : rawBody.substring(0,
                            Math.min(rawBody.length(), TransactionSyncRequest.MAX_RAW_TEXT_LENGTH)));

                    TransactionResponse saved = transactionService.processAndSave(req);
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
                if (msg.getInternalDateMs() != null && msg.getInternalDateMs() > progress.maxDate) {
                    progress.maxDate = msg.getInternalDateMs();
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
