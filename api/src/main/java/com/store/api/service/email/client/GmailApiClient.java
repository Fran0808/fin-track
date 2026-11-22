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
import java.util.function.Consumer;
import java.util.HashSet;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.store.api.model.dto.email.EmailMessageDto;
import com.store.api.model.enums.EmailSyncFailure;
import com.store.api.service.email.EmailSyncException;

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
    private static final Set<String> RATE_LIMIT_REASONS = Set.of("rateLimitExceeded", "userRateLimitExceeded",
            "dailyLimitExceeded", "quotaExceeded", "RESOURCE_EXHAUSTED", "RATE_LIMIT_EXCEEDED", "QUOTA_EXCEEDED");
    private static final Set<String> OTHER_SAFE_REASONS = Set.of("insufficientPermissions", "domainPolicy",
            "forbidden", "authError", "accessNotConfigured", "backendError", "notFound", "badRequest",
            "PERMISSION_DENIED", "UNAUTHENTICATED", "ACCESS_TOKEN_SCOPE_INSUFFICIENT", "SERVICE_DISABLED");

    private final ObjectMapper objectMapper;
    private final RestClient restClient = com.store.api.config.ExternalHttp.client();

    public List<EmailMessageDto> fetchFinancialEmails(String accessToken, int maxResults) {
        return fetchFinancialEmails(accessToken, maxResults, null, id -> false);
    }

    public List<EmailMessageDto> fetchFinancialEmails(
            String accessToken,
            int maxResults,
            Long lastSyncedEpochMs,
            Predicate<String> isProcessedFilter) {
        List<EmailMessageDto> emailMessages = new ArrayList<>();
        visitFinancialEmails(accessToken, maxResults, lastSyncedEpochMs, isProcessedFilter, emailMessages::add);
        return emailMessages;
    }

    public void visitFinancialEmails(String accessToken, int pageSize, Long lastSyncedEpochMs,
            Predicate<String> isProcessedFilter, Consumer<EmailMessageDto> consumer) {
        if (pageSize < 1 || pageSize > 500) throw new IllegalArgumentException("Gmail page size must be between 1 and 500");
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
            String pageToken = null;
            Set<String> seenPages = new HashSet<>();
            do {
            final String currentPage = pageToken;
            String listRaw = restClient.get()
                    .uri(GMAIL_MESSAGES_ENDPOINT, uriBuilder -> uriBuilder
                            .queryParam("q", searchQuery)
                            .queryParam("maxResults", pageSize)
                            .queryParamIfPresent("pageToken", java.util.Optional.ofNullable(currentPage))
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode listJson = objectMapper.readTree(listRaw);
            JsonNode messagesNode = listJson.path("messages");

            if (messagesNode.isArray()) for (JsonNode msgRef : messagesNode) {
                String messageId = msgRef.path("id").asText();
                if (isProcessedFilter != null && isProcessedFilter.test(messageId)) {
                    continue;
                }
                    EmailMessageDto dto = fetchMessageDetails(accessToken, messageId);
                    if (dto != null) {
                        consumer.accept(dto);
                    }
            }
            pageToken = listJson.path("nextPageToken").asText(null);
            if (pageToken != null && pageToken.isBlank()) pageToken = null;
            if (pageToken != null && !seenPages.add(pageToken)) throw new IllegalStateException("Gmail repeated a page token");
            } while (pageToken != null);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String reason = safeErrorReason(ex);
            EmailSyncFailure failure = status == 403 && RATE_LIMIT_REASONS.contains(reason)
                    ? EmailSyncFailure.RATE_LIMITED : EmailSyncException.classify(ex);
            log.warn("Gmail synchronization could not finish: status={}, reason={}, failure={}", status, reason, failure);
            throw new EmailSyncException(failure);
        } catch (Exception ex) {
            EmailSyncFailure failure = EmailSyncException.classify(ex);
            log.warn("Gmail synchronization could not finish: failure={}", failure);
            throw new EmailSyncException(failure);
        }
    }

    private String safeErrorReason(RestClientResponseException response) {
        try {
            JsonNode error = objectMapper.readTree(response.getResponseBodyAsString()).path("error");
            String safeReason = "unknown";
            for (String field : List.of("errors", "details")) {
                for (JsonNode detail : error.path(field)) {
                    String reason = detail.path("reason").asText("");
                    if (RATE_LIMIT_REASONS.contains(reason)) return reason;
                    if (OTHER_SAFE_REASONS.contains(reason)) safeReason = reason;
                }
            }
            String status = error.path("status").asText("");
            if (RATE_LIMIT_REASONS.contains(status)) return status;
            if ("unknown".equals(safeReason) && OTHER_SAFE_REASONS.contains(status)) return status;
            return safeReason;
        } catch (tools.jackson.core.JacksonException ex) {
            return "unknown";
        }
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

        long internalDateMs = msgJson.path("internalDate").asLong(0);
        if (internalDateMs <= 0 || !payload.isObject()) throw new IllegalStateException("Gmail message is incomplete");
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
            throw new IllegalStateException("Gmail message body could not be decoded");
        }
    }
}
