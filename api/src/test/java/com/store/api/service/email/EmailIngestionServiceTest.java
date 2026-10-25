package com.store.api.service.email;

import com.store.api.model.dto.transaction.TransactionResponse;
import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.ProcessedEmailMessageRepository;
import com.store.api.service.transaction.TransactionService;
import com.store.api.service.auth.GoogleOAuthService;
import com.store.api.service.email.client.GmailApiClient;
import com.store.api.service.email.client.ImapEmailClient;
import com.store.api.service.email.parser.BankEmailParserDispatcher;
import com.store.api.service.email.parser.BcpEmailParser;
import com.store.api.service.email.parser.YapeEmailParser;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.store.api.config.security.UserContext;
import com.store.api.model.entity.User;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailIngestionServiceTest {

    @Mock
    private GoogleOAuthService googleOAuthService;

    @Mock
    private GmailApiClient gmailApiClient;

    @Mock
    private ImapEmailClient imapEmailClient;

    @Mock
    private BankEmailParserDispatcher parserDispatcher;

    @Mock
    private TransactionService transactionService;

    @Mock
    private ProcessedEmailMessageRepository processedEmailRepository;

    @InjectMocks
    private EmailIngestionService emailIngestionService;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser(User.builder().id(42L).build());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void anonymousSyncDoesNotSelectAnotherAccountOrAccessEmail() {
        UserContext.clear();
        assertEquals(401, assertThrows(ResponseStatusException.class,
                () -> emailIngestionService.syncEmails()).getStatusCode().value());
        verifyNoInteractions(googleOAuthService, gmailApiClient, imapEmailClient,
                parserDispatcher, transactionService, processedEmailRepository);
    }

    @Test
    void forwardsNormalizedOperationEvidenceWithoutProviderSpecificRules() {
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of("synthetic-token"));
        stubGmail(null, List.of(EmailMessageDto.builder().messageId("normalized-operation").body("Synthetic confirmation").build()));
        when(parserDispatcher.dispatchAndParse(any(), any(), any(), any())).thenReturn(Optional.of(
                ParsedEmailTransaction.builder().amount(new BigDecimal("81.25")).flowType(FlowType.INTERNAL_TRANSFER)
                        .operationType(com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT)
                        .merchantName("Reference only").channel("EXAMPLE_WALLET")
                        .transactionDate(LocalDateTime.of(2026, 1, 1, 12, 0)).transactionHash("normalized-hash").build()));
        when(transactionService.processAndSave(any())).thenReturn(TransactionResponse.builder().id(1L).build());
        assertEquals("SUCCESS", emailIngestionService.syncEmails().getStatus());
        verify(transactionService).processAndSave(argThat(request ->
                request.getOperationType() == com.store.api.model.enums.OperationType.OWN_CARD_PAYMENT
                        && request.getFlowType() == FlowType.INTERNAL_TRANSFER
                        && "EXAMPLE_WALLET".equals(request.getChannel())
                        && "normalized-hash".equals(request.getTransactionHash())));
    }

    @Test
    void shouldMarkTheSurveyProcessedWithoutSavingATransaction() {
        ReflectionTestUtils.setField(emailIngestionService, "parserDispatcher",
                new BankEmailParserDispatcher(List.of(new YapeEmailParser(), new BcpEmailParser())));
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of("test-access-token"));
        EmailMessageDto survey = EmailMessageDto.builder()
                .messageId("survey-regression")
                .from("notificaciones@notificacionesbcp.com.pe")
                .subject("Ayúdanos a saber lo que necesitas")
                .body("Te invitamos a participar de una encuesta. Participa por Gift Cards de S/ 500.")
                .sentDate(LocalDateTime.of(2026, 10, 1, 12, 39, 26))
                .internalDateMs(1790876366000L)
                .build();
        stubGmail(null, List.of(survey));

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getScannedCount());
        assertEquals(0, response.getSavedCount());
        verifyNoInteractions(transactionService);
        verify(processedEmailRepository).save(argThat(message ->
                message.getMessageId().startsWith("v2:") && !message.getMessageId().contains("survey-regression")));
        verify(googleOAuthService).updateLastSyncedInternalDate(1790876366000L);
    }

    @Test
    void largeEmailIsParsedInFullWhileOnlyTheStoredDiagnosticCopyIsBounded() {
        String body = "x".repeat(70000);
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of("synthetic-token"));
        stubGmail(null, List.of(EmailMessageDto.builder().messageId("large-template").body(body).build()));
        when(parserDispatcher.dispatchAndParse(any(), any(), any(), any())).thenReturn(Optional.of(
                ParsedEmailTransaction.builder().amount(new BigDecimal("2.30")).flowType(FlowType.EXPENSE)
                        .merchantName("Example").transactionDate(LocalDateTime.of(2026, 10, 1, 12, 0))
                        .transactionHash("full-body-hash").rawBody(body).build()));
        when(transactionService.processAndSave(any())).thenReturn(TransactionResponse.builder().id(1L).build());
        assertEquals("SUCCESS", emailIngestionService.syncEmails().getStatus());
        verify(parserDispatcher).dispatchAndParse(any(), any(), eq(body), any());
        verify(transactionService).processAndSave(argThat(request -> request.getRawNotificationText().length() == 65536
                && "full-body-hash".equals(request.getTransactionHash()) && new BigDecimal("2.30").equals(request.getAmount())));
    }

    @Test
    void shouldPerformIncrementalSyncAndAdvanceCursor() {
        String token = "mock-access-token";
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of(token));
        when(googleOAuthService.getLastSyncedInternalDate()).thenReturn(Optional.of(1700000000000L));

        EmailMessageDto msg = EmailMessageDto.builder()
                .messageId("msg_test_1001")
                .subject("¡Tu pago en COMERCIO MOCK fue exitoso!")
                .from("notificaciones@yape.pe")
                .sentDate(LocalDateTime.now())
                .internalDateMs(1700000060000L)
                .body("Monto total S/ 15.00")
                .build();

        stubGmail(1700000000000L, List.of(msg));

        ParsedEmailTransaction parsedTx = ParsedEmailTransaction.builder()
                .amount(new BigDecimal("15.00"))
                .flowType(FlowType.EXPENSE)
                .merchantName("COMERCIO MOCK")
                .channel("YAPE")
                .transactionDate(LocalDateTime.now())
                .transactionHash("hash_mock_123456")
                .rawBody("Monto total S/ 15.00")
                .build();

        when(parserDispatcher.dispatchAndParse(any(), any(), any(), any()))
                .thenReturn(Optional.of(parsedTx));
        when(transactionService.processAndSave(any()))
                .thenReturn(TransactionResponse.builder().id(1L).amount(new BigDecimal("15.00")).build());

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertNotNull(response);
        assertEquals(1, response.getScannedCount());
        assertEquals(1, response.getSavedCount());
        assertEquals("SUCCESS", response.getStatus());

        verify(processedEmailRepository, times(1)).save(any());
        verify(googleOAuthService, times(1)).updateLastSyncedInternalDate(1700000060000L);
        verify(googleOAuthService).recordSyncResult(true);
    }

    @Test
    void shouldHandleEmptyMessagesGracefullyWhenNoNewEmails() {
        String token = "mock-access-token";
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of(token));
        when(googleOAuthService.getLastSyncedInternalDate()).thenReturn(Optional.of(1700000000000L));

        stubGmail(1700000000000L, List.of());

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertNotNull(response);
        assertEquals(0, response.getScannedCount());
        assertEquals(0, response.getSavedCount());
        verify(processedEmailRepository, never()).save(any());
        verify(googleOAuthService, never()).updateLastSyncedInternalDate(anyLong());
        verify(googleOAuthService).recordSyncResult(true);
    }

    @Test
    void shouldReportPartialSyncWhenMessageProcessingFails() {
        String token = "mock-access-token";
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of(token));
        EmailMessageDto message = EmailMessageDto.builder()
                .messageId("failed_message")
                .internalDateMs(1700000060000L)
                .build();
        stubGmail(null, List.of(message));
        when(parserDispatcher.dispatchAndParse(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Malformed message"));

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertEquals("PARTIAL", response.getStatus());
        verify(googleOAuthService).recordSyncResult(false);
        verify(processedEmailRepository, never()).save(any());
        verify(googleOAuthService, never()).updateLastSyncedInternalDate(anyLong());
    }

    private void stubGmail(Long cursor, List<EmailMessageDto> messages) {
        doAnswer(invocation -> {
            java.util.function.Consumer<EmailMessageDto> consumer = invocation.getArgument(4);
            messages.forEach(consumer);
            return null;
        }).when(gmailApiClient).visitFinancialEmails(anyString(), anyInt(), eq(cursor), any(), any());
    }

    @Test
    void disconnectedUserCannotFallBackToGlobalImap() {
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.empty());
        assertEquals(409, assertThrows(ResponseStatusException.class, emailIngestionService::syncEmails).getStatusCode().value());
        assertEquals("ERROR", emailIngestionService.testConnection().getStatus());
        verifyNoInteractions(imapEmailClient, gmailApiClient, processedEmailRepository, transactionService);
    }

    @Test
    void failedPageDoesNotAdvanceCursor() {
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of("synthetic-token"));
        doThrow(new IllegalStateException("simulated page failure")).when(gmailApiClient)
                .visitFinancialEmails(anyString(), anyInt(), isNull(), any(), any());
        assertThrows(IllegalStateException.class, emailIngestionService::syncEmails);
        verify(googleOAuthService, never()).updateLastSyncedInternalDate(anyLong());
        verify(googleOAuthService).recordSyncResult(false);
    }

    @Test
    void messageMarkersAreNamespacedByUser() {
        when(googleOAuthService.getValidAccessToken()).thenReturn(Optional.of("synthetic-token"));
        stubGmail(null, List.of(EmailMessageDto.builder().messageId("same-provider-id").build()));
        emailIngestionService.syncEmails();
        UserContext.setCurrentUser(User.builder().id(43L).build());
        emailIngestionService.syncEmails();
        var captor = org.mockito.ArgumentCaptor.forClass(com.store.api.model.entity.ProcessedEmailMessage.class);
        verify(processedEmailRepository, times(2)).save(captor.capture());
        assertNotEquals(captor.getAllValues().get(0).getMessageId(), captor.getAllValues().get(1).getMessageId());
        assertEquals(42L, captor.getAllValues().get(0).getUser().getId());
        assertEquals(43L, captor.getAllValues().get(1).getUser().getId());
    }
}
