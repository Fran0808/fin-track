package com.store.api.service.email;

import com.store.api.model.dto.TransactionResponse;
import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.dto.email.EmailSyncResponse;
import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.FlowType;
import com.store.api.repository.ProcessedEmailMessageRepository;
import com.store.api.service.TransactionService;
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
        when(gmailApiClient.fetchFinancialEmails(anyString(), anyInt(), isNull(), any()))
                .thenReturn(List.of(survey));

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(1, response.getScannedCount());
        assertEquals(0, response.getSavedCount());
        verifyNoInteractions(transactionService);
        verify(processedEmailRepository).save(argThat(message ->
                "survey-regression".equals(message.getMessageId())));
        verify(googleOAuthService).updateLastSyncedInternalDate(1790876366000L);
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

        when(gmailApiClient.fetchFinancialEmails(eq(token), anyInt(), eq(1700000000000L), any()))
                .thenReturn(List.of(msg));
        when(processedEmailRepository.existsById("msg_test_1001")).thenReturn(false);

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

        when(gmailApiClient.fetchFinancialEmails(eq(token), anyInt(), eq(1700000000000L), any()))
                .thenReturn(List.of());

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
        when(gmailApiClient.fetchFinancialEmails(eq(token), anyInt(), isNull(), any()))
                .thenReturn(List.of(message));
        when(parserDispatcher.dispatchAndParse(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Malformed message"));

        EmailSyncResponse response = emailIngestionService.syncEmails();

        assertEquals("PARTIAL", response.getStatus());
        verify(googleOAuthService).recordSyncResult(false);
        verify(processedEmailRepository, never()).save(any());
        verify(googleOAuthService, never()).updateLastSyncedInternalDate(anyLong());
    }
}
