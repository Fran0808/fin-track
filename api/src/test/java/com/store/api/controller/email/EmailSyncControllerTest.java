package com.store.api.controller.email;

import com.store.api.model.enums.EmailSyncFailure;
import com.store.api.service.email.EmailIngestionService;
import com.store.api.service.email.EmailSyncException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EmailSyncControllerTest {
    @ParameterizedTest
    @CsvSource({"RATE_LIMITED,429,Espera unos minutos", "AUTH_REQUIRED,409,Vuelve a conectar Gmail",
            "ACCESS_DENIED,403,diagnóstico de correo", "TEMPORARY,503,Inténtalo más tarde",
            "UNKNOWN,502,Los movimientos ya guardados se conservan"})
    void returnsAnActionableMessageForGmailFailures(EmailSyncFailure failure, int code, String advice) throws Exception {
        EmailIngestionService ingestion = mock(EmailIngestionService.class);
        when(ingestion.syncEmails()).thenThrow(new EmailSyncException(failure));

        MockMvcBuilders.standaloneSetup(new EmailSyncController(ingestion)).build()
                .perform(post("/api/v1/emails/sync"))
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.message").value(containsString(advice)));
    }
}
