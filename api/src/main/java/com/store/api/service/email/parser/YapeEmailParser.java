package com.store.api.service.email.parser;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.ChannelType;
import com.store.api.model.enums.FlowType;

import lombok.extern.slf4j.Slf4j;

/**
 * Dedicated parser for Yape transaction confirmation emails.
 * Handles merchant payments, peer-to-peer yapeos, and incoming transfers.
 * Ignores administrative/setting emails (such as limit changes).
 */
@Component
@Order(1)
@Slf4j
public class YapeEmailParser implements BankEmailParser {

    private static final Set<String> ALLOWED_DOMAINS = Set.of("yape.pe");
    private static final Pattern EXPENSE_CONFIRMATION_PATTERN = Pattern.compile(
            "(?:tu\\s+pago\\s+en\\s+.+?\\s+fue\\s+exitoso|yapeaste\\s+a|enviaste\\s+un\\s+yape\\s+a)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INCOME_CONFIRMATION_PATTERN = Pattern.compile(
            "(?:te\\s+yapearon|te\\s+yape[oó]|recibiste\\s+un\\s+yape)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String MONEY = "(?:S/\\.?|PEN)\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)";
    private static final Pattern LABELED_AMOUNT_PATTERN = Pattern.compile(
            "\\b(?:monto\\s+total|monto|importe)\\s*(?::|es\\s+de|por)?\\s*" + MONEY,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INLINE_AMOUNT_PATTERN = Pattern.compile(
            "(?:te\\s+yape[oó]|yapeaste\\s+a|enviaste\\s+un\\s+yape\\s+a|recibiste\\s+un\\s+yape)\\s+[^.!?]{0,100}?\\s*" + MONEY,
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern MERCHANT_HEADER_PATTERN = Pattern.compile(
            "(?:¡?tu\\s+pago\\s+en\\s+)(.+?)(?:\\s+fue\\s+exitoso)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DESTINO_PATTERN = Pattern.compile(
            "destino:?\\s*([A-Za-z0-9À-ÿ\\s.,&'-]+?)(?=\\s*(?:id|fecha|titular|número|numero|$))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern YAPEASTE_A_PATTERN = Pattern.compile(
            "(?:¡?yapeaste\\s+a\\s+|enviaste\\s+un\\s+yape\\s+a\\s+)(.+?)(?=\\s*(?:!|\\.|\\s+por|\\s+de|\\s+un\\s+monto|\\s+S/\\.?|\\s+PEN|\\s+[0-9]|$))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TE_YAPEO_PATTERN = Pattern.compile(
            "(?:te\\s+yape[oó]\\s+)(.+?)(?=\\s*(?:!|\\.|\\s+por|\\s+de|\\s+un\\s+monto|\\s+S/\\.?|\\s+PEN|\\s+[0-9]|$))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OP_ID_PATTERN = Pattern.compile(
            "(?:id\\s+de\\s+operaci[oó]n|operaci[oó]n|nro\\.?\\s*operaci[oó]n):?\\s*([A-Za-z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "fecha\\s*(?:y\\s*hora)?:?\\s*([0-9]{1,2}[/-][0-9]{1,2}[/-][0-9]{4}|[0-9]{1,2}\\s+[a-zÀ-ÿ]+\\s+[0-9]{4})",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String sender, String subject) {
        return BankEmailSenderPolicy.matchesDomain(sender, ALLOWED_DOMAINS);
    }

    @Override
    public String getBankName() {
        return "YAPE";
    }

    @Override
    public ParsedEmailTransaction parse(String subject, String htmlOrTextBody, LocalDateTime receivedDate) {
        String cleanText = extractPlainText(htmlOrTextBody);
        String combined = (subject != null ? subject : "") + " " + cleanText;

        if (cleanText.isBlank()) {
            return null;
        }

        String lowerCombined = combined.toLowerCase();
        if (lowerCombined.contains("cambios en monto") ||
            lowerCombined.contains("actualizaste el monto") ||
            lowerCombined.contains("código de verificación") ||
            lowerCombined.contains("codigo de verificacion")) {
            log.info("Ignoring Yape non-financial configuration email: [{}]", subject);
            return null;
        }

        boolean expense = EXPENSE_CONFIRMATION_PATTERN.matcher(combined).find();
        boolean income = INCOME_CONFIRMATION_PATTERN.matcher(combined).find();
        if (expense == income) {
            return null;
        }
        FlowType flowType = income ? FlowType.INCOME : FlowType.EXPENSE;

        BigDecimal amount = extractAmount(cleanText);
        if (amount == null) {
            log.warn("Could not extract valid amount from Yape email: [{}]", subject);
            return null;
        }

        String merchant = extractMerchantOrContact(combined, cleanText, flowType);

        String operationNumber = extractOperationId(cleanText);

        LocalDateTime txDate = receivedDate != null ? receivedDate : LocalDateTime.now();

        String hash = generateHash(flowType, amount, merchant, operationNumber, txDate);

        return ParsedEmailTransaction.builder()
                .amount(amount)
                .currency("PEN")
                .flowType(flowType)
                .merchantName(merchant)
                .channel(ChannelType.YAPE.name())
                .cardLast4(null)
                .operationNumber(operationNumber)
                .transactionDate(txDate)
                .transactionHash(hash)
                .rawBody(cleanText)
                .build();
    }

    private BigDecimal extractAmount(String text) {
        Map<String, ParsedAmount> amounts = new LinkedHashMap<>();
        collectAmounts(LABELED_AMOUNT_PATTERN.matcher(text), amounts);
        collectAmounts(INLINE_AMOUNT_PATTERN.matcher(text), amounts);
        return amounts.size() == 1 ? amounts.values().iterator().next().amount() : null;
    }

    private void collectAmounts(Matcher matcher, Map<String, ParsedAmount> amounts) {
        while (matcher.find()) {
            BigDecimal amount = new BigDecimal(matcher.group(1).replace(",", ""));
            if (amount.signum() <= 0) {
                continue;
            }
            String key = amount.stripTrailingZeros().toPlainString();
            ParsedAmount existing = amounts.get(key);
            if (existing == null || matcher.start(1) < existing.position()) {
                amounts.put(key, new ParsedAmount(amount, matcher.start(1)));
            }
        }
    }

    private record ParsedAmount(BigDecimal amount, int position) {
    }

    private String extractMerchantOrContact(String combined, String cleanText, FlowType flowType) {
        if (flowType == FlowType.INCOME) {
            Matcher teYapeo = TE_YAPEO_PATTERN.matcher(cleanText);
            if (teYapeo.find()) {
                return cleanName(teYapeo.group(1));
            }
            return "Yape Recibido";
        }

        Matcher headerMatcher = MERCHANT_HEADER_PATTERN.matcher(combined);
        if (headerMatcher.find()) {
            return cleanName(headerMatcher.group(1));
        }

        Matcher destinoMatcher = DESTINO_PATTERN.matcher(cleanText);
        if (destinoMatcher.find()) {
            return cleanName(destinoMatcher.group(1));
        }

        Matcher yapeasteMatcher = YAPEASTE_A_PATTERN.matcher(combined);
        if (yapeasteMatcher.find()) {
            return cleanName(yapeasteMatcher.group(1));
        }

        return "Yape";
    }

    private String cleanName(String name) {
        if (name == null) return "Yape";
        String cleaned = name.replaceAll("(?i)^(el\\s+|la\\s+)", "")
                .replaceAll("[.,;:!]+$", "")
                .trim();
        return cleaned.isEmpty() ? "Yape" : cleaned;
    }

    private String extractOperationId(String text) {
        Matcher m = OP_ID_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
    }

    private String generateHash(FlowType flowType, BigDecimal amount, String merchant, String opNumber, LocalDateTime date) {
        String dateKey = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String opKey = (opNumber != null && !opNumber.isBlank()) ? opNumber : date.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String input = flowType.name() + "_" + amount.toPlainString() + "_" + merchant.toLowerCase(Locale.ROOT).trim() + "_" + opKey + "_" + dateKey;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : encoded) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String extractPlainText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            return Jsoup.parse(content).text().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return content.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        }
    }
}
