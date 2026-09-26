package com.store.api.service.email;

import com.store.api.model.dto.TransactionResponse;
import com.store.api.model.dto.TransactionSyncRequest;
import com.store.api.model.dto.email.EmailConnectionTestResponse;
import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.ProcessedEmailMessage;
import com.store.api.repository.ProcessedEmailMessageRepository;
import com.store.api.service.TransactionService;
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
        Optional<String> googleToken = googleOAuthService.getValidAccessToken();
        if (googleToken.isPresent()) {
            return EmailConnectionTestResponse.builder()
                    .status("OK")
                    .message("Conexión activa y autorizada con Google Gmail API (OAuth2)")
                    .build();
        }
        return imapEmailClient.testConnection();
    }

    public boolean hasActiveConnection() {
        return googleOAuthService.getValidAccessToken().isPresent();
    }

    public EmailSyncResponse syncEmails() {
        UserContext.requireCurrentUser();

        try {
            List<EmailMessageDto> messages;
            Optional<String> googleToken = googleOAuthService.getValidAccessToken();

        if (googleToken.isEmpty() && UserContext.getCurrentUser() != null && googleOAuthService.getStatus().isConnected()) {
            throw new IllegalStateException("The connected Gmail account is unavailable for synchronization");
        }

        if (googleToken.isPresent()) {
            log.info("Synchronizing emails using Google Gmail REST API (OAuth2)...");
            Long lastSyncedInternalDate = googleOAuthService.getLastSyncedInternalDate().orElse(null);
            messages = gmailApiClient.fetchFinancialEmails(
                    googleToken.get(),
                    batchSize,
                    lastSyncedInternalDate,
                    processedEmailRepository::existsById
            );
        } else {
            log.info("Synchronizing emails using IMAP fallback client...");
            messages = imapEmailClient.fetchFinancialEmails(batchSize);
        }
        int scannedCount = messages.size();
        int savedCount = 0;
        int processingErrors = 0;
        List<TransactionResponse> savedTransactions = new ArrayList<>();

        long maxInternalDateMs = 0L;

        for (EmailMessageDto msg : messages) {
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
                    req.setRawNotificationText(parsed.getRawBody());

                    TransactionResponse saved = transactionService.processAndSave(req);
                    savedTransactions.add(saved);
                    savedCount++;
                }

                if (msg.getMessageId() != null && !processedEmailRepository.existsById(msg.getMessageId())) {
                    processedEmailRepository.save(ProcessedEmailMessage.builder()
                            .messageId(msg.getMessageId())
                            .internalDateMs(msg.getInternalDateMs())
                            .subject(msg.getSubject())
                            .build());
                }
                if (msg.getInternalDateMs() != null && msg.getInternalDateMs() > maxInternalDateMs) {
                    maxInternalDateMs = msg.getInternalDateMs();
                }
            } catch (Exception ex) {
                processingErrors++;
                log.warn("Error processing email message ID [{}]: {}", msg.getMessageId(), ex.getMessage());
            }
        }

        if (processingErrors == 0 && maxInternalDateMs > 0) {
            googleOAuthService.updateLastSyncedInternalDate(maxInternalDateMs);
        }

        log.info("Email synchronization completed: scanned={}, saved={}", scannedCount, savedCount);
        googleOAuthService.recordSyncResult(processingErrors == 0);

            return EmailSyncResponse.builder()
                    .status(processingErrors == 0 ? "SUCCESS" : "PARTIAL")
                    .scannedCount(scannedCount)
                    .processedInBatch(messages.size())
                    .savedCount(savedCount)
                    .transactions(savedTransactions)
                    .message(processingErrors == 0
                            ? "Emails synchronized successfully"
                            : "Some email messages could not be processed")
                    .build();
        } catch (RuntimeException ex) {
            googleOAuthService.recordSyncResult(false);
            throw ex;
        }
    }
}
