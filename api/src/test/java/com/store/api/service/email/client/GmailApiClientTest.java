package com.store.api.service.email.client;

import com.store.api.model.dto.email.EmailMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GmailApiClientTest {

    private GmailApiClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GmailApiClient(new ObjectMapper());
        ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    @Test
    void shouldNotInventABankSenderWhenFromIsMissing() {
        expectMessage("{\"name\":\"Subject\",\"value\":\"Constancia de transferencia BCP\"}");
        List<EmailMessageDto> messages = client.fetchFinancialEmails("test-access-token", 20);
        assertEquals(1, messages.size());
        assertEquals("", messages.getFirst().getFrom());
        assertEquals("Monto: S/ 500", messages.getFirst().getBody());
        server.verify();
    }

    @Test
    void shouldPreserveTheActualSenderHeader() {
        expectMessage("{\"name\":\"From\",\"value\":\"Survey <survey@example.test>\"}");
        List<EmailMessageDto> messages = client.fetchFinancialEmails("test-access-token", 20);
        assertEquals("Survey <survey@example.test>", messages.getFirst().getFrom());
        server.verify();
    }

    private void expectMessage(String headerJson) {
        String endpoint = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
        server.expect(requestTo(startsWith(endpoint + "?"))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"message-1\"}]}", MediaType.APPLICATION_JSON));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Monto: S/ 500".getBytes(StandardCharsets.UTF_8));
        String detail = "{\"internalDate\":1790876366000,\"payload\":{\"headers\":["
                + headerJson + "],\"body\":{\"data\":\"" + body + "\"}}}";
        server.expect(requestTo(endpoint + "/message-1?format=full")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detail, MediaType.APPLICATION_JSON));
    }
}
