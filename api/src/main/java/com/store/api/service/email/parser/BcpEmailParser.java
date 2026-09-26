package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.ChannelType;
import com.store.api.model.enums.FlowType;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;

@Component
@Order(2)
public class BcpEmailParser implements BankEmailParser {

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:(?:importe|monto|total)\\s*(?::|es\\s+de|por)?\\s*(S/\\.?|US\\$|\\$|USD|PEN)?\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?))|" +
            "(?:(S/\\.?|US\\$|\\$|USD|PEN)\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
            "(?:establecimiento|comercio|empresa|destino|beneficiario|a\\s+favor\\s+de):?\\s*([A-Za-z0-9À-ÿ\\s.,&'/*#_+-]+?)(?=\\s*(?:fecha|importe|monto|nro|número|numero|tarjeta|operaci[oó]n|$))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CONSUMO_EN_PATTERN = Pattern.compile(
            "(?:consumo\\s+(?:de\\s+[^\\s]+\\s+)?(?:con\\s+[^\\s]+\\s+)?en\\s+)([A-Za-z0-9À-ÿ\\s.,&'/*#_+-]+?)(?=[.,;]|\\s*(?:por\\s+tu\\s+seguridad|fecha|monto|importe|$))",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CARD_LAST4_PATTERN = Pattern.compile(
            "(?:terminada\\s+en|tarjeta\\s*\\*+|\\*{3,})\\s*([0-9]{4})",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OP_NUMBER_PATTERN = Pattern.compile(
            "(?:nro\\.?|número|numero)\\s*(?:de)?\\s*operaci[oó]n:?\\s*([0-9]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public boolean supports(String sender, String subject) {
        String cleanSender = sender != null ? sender.toLowerCase() : "";
        String cleanSubject = subject != null ? subject.toLowerCase() : "";

        return cleanSender.contains("notificacionesbcp.com.pe") ||
                cleanSender.contains("bcp") ||
                cleanSubject.contains("bcp");
    }

    @Override
    public String getBankName() {
        return "BCP";
    }

    @Override
    public ParsedEmailTransaction parse(String subject, String htmlOrTextBody, LocalDateTime receivedDate) {
        String cleanText = extractPlainText(htmlOrTextBody);
        String combined = (subject != null ? subject : "") + " " + cleanText;

        if (cleanText.isBlank()) {
            return null;
        }

        // 1. Determine Channel
        String channel = determineChannel(subject, combined);

        // 2. Extract Amount and Currency
        BigDecimal amount = null;
        String currency = "PEN";
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(combined);
        while (amountMatcher.find()) {
            String currencyGroup = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(3);
            String numberGroup = amountMatcher.group(2) != null ? amountMatcher.group(2) : amountMatcher.group(4);

            if (numberGroup != null && !numberGroup.isBlank()) {
                try {
                    String cleanNumber = numberGroup.replace(",", "").trim();
                    BigDecimal candidate = new BigDecimal(cleanNumber);
                    if (candidate.compareTo(BigDecimal.ZERO) > 0) {
                        amount = candidate;
                        if (currencyGroup != null && (currencyGroup.contains("$") || currencyGroup.equalsIgnoreCase("USD"))) {
                            currency = "USD";
                        }
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (amount == null) {
            return null;
        }

        String lowerCombined = combined.toLowerCase();

        // 3. Extract Merchant / Recipient
        String merchant = "Consumo BCP";

        if (lowerCombined.contains("pago de tarjeta propia") || lowerCombined.contains("pago a tu tarjeta") || lowerCombined.contains("pago de tarjeta de crédito propia") || lowerCombined.contains("pago de tarjeta de credito propia")) {
            merchant = "Pago Tarjeta Crédito BCP";
        } else if (lowerCombined.contains("recibiste un yapeo") || lowerCombined.contains("yapeo a celular")) {
            Matcher yapeoDeMatcher = Pattern.compile("(?:recibiste un yapeo de\\s+[^\\s]+\\s+[0-9.,]+\\s+de|enviado por):?\\s*([A-Za-z0-9À-ÿ\\s.,&'-]+?)(?=\\s*(?:\\.|por tu seguridad|¿no reconoces|$))", Pattern.CASE_INSENSITIVE).matcher(cleanText);
            if (yapeoDeMatcher.find()) {
                merchant = yapeoDeMatcher.group(1).trim();
            } else {
                merchant = "Yape Recibido";
            }
        } else {
            Matcher empresaMatcher = Pattern.compile("empresa:?\\s*([A-Za-z0-9À-ÿ\\s.,&'-]+?)(?=\\s*(?:servicio|titular|código|cuenta|monto|$))", Pattern.CASE_INSENSITIVE).matcher(cleanText);
            if (empresaMatcher.find()) {
                merchant = empresaMatcher.group(1).trim();
            } else {
                Matcher merchantMatcher = MERCHANT_PATTERN.matcher(cleanText);
                if (merchantMatcher.find()) {
                    String found = merchantMatcher.group(1).replaceAll("[.,;:]+$", "").trim();
                    if (found.length() >= 3 && !found.equalsIgnoreCase("bcp")) {
                        merchant = found;
                    }
                }
                if ("Consumo BCP".equals(merchant)) {
                    Matcher enMatcher = CONSUMO_EN_PATTERN.matcher(cleanText);
                    if (enMatcher.find()) {
                        String found = enMatcher.group(1).replaceAll("[.,;:]+$", "").trim();
                        if (found.length() >= 3 && !found.equalsIgnoreCase("bcp")) {
                            merchant = found;
                        }
                    }
                }
            }
        }

        // 4. Extract Card Last 4
        String cardLast4 = null;
        Matcher cardMatcher = CARD_LAST4_PATTERN.matcher(combined);
        if (cardMatcher.find()) {
            cardLast4 = cardMatcher.group(1);
        }

        // 5. Extract Operation Number
        String operationNumber = null;
        Matcher opMatcher = OP_NUMBER_PATTERN.matcher(cleanText);
        if (opMatcher.find()) {
            operationNumber = opMatcher.group(1);
        }

        // 6. FlowType (Default is EXPENSE for cards/consumption, check if internal transfer or income)
        FlowType flowType = FlowType.EXPENSE;

        if (lowerCombined.contains("entre mis cuentas") || lowerCombined.contains("transferencia propia") || lowerCombined.contains("transferencia entre cuentas")) {
            flowType = FlowType.INTERNAL_TRANSFER;
        } else if (lowerCombined.contains("te envió") || lowerCombined.contains("te yapeó") || lowerCombined.contains("recibiste un yapeo") || lowerCombined.contains("recepción de yapeo") || lowerCombined.contains("abono")) {
            flowType = FlowType.INCOME;
        }

        LocalDateTime txDate = receivedDate != null ? receivedDate : LocalDateTime.now();
        String hash = generateHash(flowType, amount, merchant, operationNumber, txDate);

        return ParsedEmailTransaction.builder()
                .amount(amount)
                .currency(currency)
                .flowType(flowType)
                .merchantName(merchant)
                .channel(channel)
                .cardLast4(cardLast4)
                .operationNumber(operationNumber)
                .transactionDate(txDate)
                .transactionHash(hash)
                .rawBody(cleanText)
                .build();
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

    private String determineChannel(String subject, String text) {
        String lowerSubject = (subject != null ? subject : "").toLowerCase();
        String lower = text.toLowerCase();

        if (lowerSubject.contains("yape") || lowerSubject.contains("yapeo") || lower.contains("yapeo a celular") || lower.contains("recibiste un yapeo")) {
            return ChannelType.YAPE.name();
        } else if (lowerSubject.contains("crédito") || lowerSubject.contains("credito")) {
            return ChannelType.TARJETA_CREDITO_BCP.name();
        } else if (lowerSubject.contains("débito") || lowerSubject.contains("debito")) {
            return ChannelType.TARJETA_DEBITO_BCP.name();
        } else if (lower.contains("tarjeta de crédito") || lower.contains("tarjeta de credito")) {
            return ChannelType.TARJETA_CREDITO_BCP.name();
        } else if (lower.contains("tarjeta de débito") || lower.contains("tarjeta de debito")) {
            return ChannelType.TARJETA_DEBITO_BCP.name();
        } else if (lower.contains("yape") || lower.contains("yapeo")) {
            return ChannelType.YAPE.name();
        }
        return ChannelType.BCP_TRANSFERENCIA.name();
    }

    private String generateHash(FlowType flowType, BigDecimal amount, String merchant, String opNumber, LocalDateTime date) {
        String dateKey = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String opKey = (opNumber != null && !opNumber.isBlank()) ? opNumber : date.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String input = flowType.name() + "_" + amount.toPlainString() + "_" + merchant.toLowerCase().trim() + "_" + opKey + "_" + dateKey;

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
}
