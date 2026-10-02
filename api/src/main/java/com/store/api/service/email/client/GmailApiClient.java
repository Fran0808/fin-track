package com.store.api.service.email.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.store.api.model.dto.email.EmailMessageDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Official Google Gmail REST API client for fetching financial emails securely via OAuth2 Bearer token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GmailApiClient {

    private static final String GMAIL_MESSAGES_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
    private static final ZoneId PERU_ZONE = ZoneId.of("America/Lima");

    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public List<EmailMessageDto> fetchFinancialEmails(String accessToken, int maxResults) {
        return fetchFinancialEmails(accessToken, maxResults, null, id -> false);
    }

    public List<EmailMessageDto> fetchFinancialEmails(
            String accessToken,
            int maxResults,
            Long lastSyncedEpochMs,
            Predicate<String> isProcessedFilter) {
        log.info("Fetching financial emails from Gmail REST API (maxResults={}, lastSyncedEpochMs={})...",
                maxResults, lastSyncedEpochMs);
        List<EmailMessageDto> emailMessages = new ArrayList<>();

        try {
            String baseQuery = "BCP OR Yape OR constancia OR consumo OR notificacionesbcp.com.pe OR notificaciones@yape.pe OR yape.pe";
            String query = baseQuery;

            if (lastSyncedEpochMs != null && lastSyncedEpochMs > 0) {
                // Look back 120 seconds before the cursor to absorb network delays or clock skew
                long afterSeconds = (lastSyncedEpochMs / 1000) - 120;
                if (afterSeconds > 0) {
                    query = "(" + baseQuery + ") after:" + afterSeconds;
                }
            }

            final String searchQuery = query;
            log.info("Querying Gmail REST API with q=[{}] and maxResults=[{}]", searchQuery, maxResults);

            String listRaw = restClient.get()
                    .uri(GMAIL_MESSAGES_ENDPOINT, uriBuilder -> uriBuilder
                            .queryParam("q", searchQuery)
                            .queryParam("maxResults", maxResults)
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            log.info("Gmail API search response: {}", listRaw);

            JsonNode listJson = objectMapper.readTree(listRaw);
            JsonNode messagesNode = listJson.path("messages");

            if (!messagesNode.isArray() || messagesNode.isEmpty()) {
                log.info("No matching financial emails found via Gmail API query [{}].", query);
                return emailMessages;
            }

            for (JsonNode msgRef : messagesNode) {
                String messageId = msgRef.path("id").asText();
                if (isProcessedFilter != null && isProcessedFilter.test(messageId)) {
                    log.debug("Skipping already processed Gmail message ID [{}]", messageId);
                    continue;
                }
                try {
                    EmailMessageDto dto = fetchMessageDetails(accessToken, messageId);
                    if (dto != null) {
                        emailMessages.add(dto);
                    }
                } catch (Exception ex) {
                    log.warn("Error fetching details for Gmail message [{}]: {}", messageId, ex.getMessage());
                }
            }

            log.info("Successfully fetched [{}] financial emails via Gmail REST API.", emailMessages.size());
        } catch (Exception ex) {
            log.error("Failed to query Gmail REST API messages", ex);
            throw new RuntimeException("Gmail API communication failure: " + ex.getMessage(), ex);
        }

        return emailMessages;
    }

    private EmailMessageDto fetchMessageDetails(String accessToken, String messageId) throws Exception {
        String detailUrl = GMAIL_MESSAGES_ENDPOINT + "/" + messageId + "?format=full";

        String rawDetail = restClient.get()
                .uri(detailUrl)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);

        JsonNode msgJson = objectMapper.readTree(rawDetail);
        JsonNode payload = msgJson.path("payload");

        String subject = "";
        String from = "";
        JsonNode headers = payload.path("headers");
        if (headers.isArray()) {
            for (JsonNode header : headers) {
                String name = header.path("name").asText();
                if ("Subject".equalsIgnoreCase(name)) {
                    subject = header.path("value").asText("");
                } else if ("From".equalsIgnoreCase(name)) {
                    from = header.path("value").asText(from);
                }
            }
        }

        long internalDateMs = msgJson.path("internalDate").asLong(System.currentTimeMillis());
        LocalDateTime sentDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(internalDateMs), PERU_ZONE);

        String body = extractBody(payload);

        return EmailMessageDto.builder()
                .messageId(messageId)
                .from(from)
                .subject(subject)
                .sentDate(sentDate)
                .internalDateMs(internalDateMs)
                .body(body)
                .build();
    }

    private String extractBody(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "";
        }

        // Direct body in node
        String bodyData = node.path("body").path("data").asText("");
        if (!bodyData.isBlank()) {
            return decodeBase64Url(bodyData);
        }

        // Check multipart parts
        JsonNode parts = node.path("parts");
        if (parts.isArray()) {
            // Priority 1: text/html
            for (JsonNode part : parts) {
                String mimeType = part.path("mimeType").asText("");
                if ("text/html".equalsIgnoreCase(mimeType)) {
                    String partData = part.path("body").path("data").asText("");
                    if (!partData.isBlank()) {
                        return decodeBase64Url(partData);
                    }
                }
            }

            // Priority 2: text/plain
            for (JsonNode part : parts) {
                String mimeType = part.path("mimeType").asText("");
                if ("text/plain".equalsIgnoreCase(mimeType)) {
                    String partData = part.path("body").path("data").asText("");
                    if (!partData.isBlank()) {
                        return decodeBase64Url(partData);
                    }
                }
            }

            // Priority 3: recursive nested multipart parts
            for (JsonNode part : parts) {
                String nested = extractBody(part);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }

        return "";
    }

    private String decodeBase64Url(String base64UrlString) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64UrlString);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.warn("Failed to decode base64url email body chunk: {}", ex.getMessage());
            return "";
        }
    }
}
