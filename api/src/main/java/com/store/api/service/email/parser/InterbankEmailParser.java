package com.store.api.service.email.parser;

import com.store.api.model.dto.email.ParsedEmailTransaction;
import com.store.api.model.enums.ChannelType;
import com.store.api.model.enums.FlowType;
import com.store.api.model.enums.OperationType;
import org.jsoup.Jsoup;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Email parser for Interbank Peru transaction confirmations (cards, transfers, and Plin).
 * Restricts discovery to official Interbank domains and excludes promotional/statement senders.
 */
@Component
@Order(3)
public class InterbankEmailParser implements BankEmailParser {

    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "interbank.pe",
            "tiendas.interbank.pe",
            "avisos.interbank.pe",
            "interbank.com.pe",
            "netinterbank.com.pe"
    );
    private static final int TEXT_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final String MONEY = "(S/\\.?|US\\$|\\$|USD|PEN)\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)";

    private static final Pattern NON_FINANCIAL_PATTERN = Pattern.compile(
            "(?:clave\\s+din[aá]mica|c[oó]digo\\s+de\\s+verificaci[oó]n|estado\\s+de\\s+cuenta|cambio\\s+de\\s+l[ií]mites|bloqueo\\s+de\\s+tarjeta|actualizaci[oó]n\\s+de\\s+datos|participa\\s+(?:por|en)\\s+(?:el\\s+)?sorteo)",
            TEXT_FLAGS);

    private static final Pattern LABELED_AMOUNT_PATTERN = Pattern.compile(
            "\\b(?:monto(?:\\s+(?:total|enviado|recibido|pagado|transferido|de\\s+la\\s+operaci[oó]n))?|importe(?:\\s+total)?|total\\s+(?:pagado|del\\s+consumo))\\s*(?::|es\\s+de|por)?\\s*" + MONEY,
            TEXT_FLAGS);

    private static final Pattern INLINE_AMOUNT_PATTERN = Pattern.compile(
            "(?:realizaste\\s+(?:un|una)\\s+(?:consumo|pago|transferencia|env[ií]o)(?:\\s+[^.!?]{0,80}?)?|enviaste\\s+(?:un\\s+plin|dinero)(?:\\s+[^.!?]{0,80}?)?|plineaste\\s+a\\s+[^.!?]{1,80}?|recibiste\\s+(?:un\\s+plin|(?:una\\s+)?transferencia|un\\s+abono)(?:\\s+[^.!?]{0,80}?)?|te\\s+(?:pline[oó]|envi[oó]|transfir[ií]o)\\s+[^.!?]{1,80}?)\\s*(?:por|de)?\\s*" + MONEY,
            TEXT_FLAGS);

    private static final Pattern EXPENSE_CONFIRMATION_PATTERN = Pattern.compile(
            "(?:constancia\\s+de\\s+(?:pago|transferencia|env[ií]o|operaci[oó]n)|realizaste\\s+(?:un|una)\\s+(?:consumo|pago|transferencia|env[ií]o)|consumo\\s+con\\s+(?:tu\\s+)?tarjeta|enviaste\\s+(?:un\\s+plin|dinero)|plineaste\\s+a|tu\\s+pago\\s+fue\\s+exitoso)",
            TEXT_FLAGS);

    private static final Pattern INCOME_CONFIRMATION_PATTERN = Pattern.compile(
            "(?:recibiste\\s+(?:un\\s+plin|(?:una\\s+)?transferencia|un\\s+abono)|te\\s+(?:pline[oó]|plinearon|enviaron|transfirieron)|abono\\s+recibido|transferencia\\s+recibida|constancia\\s+de\\s+(?:abono|recepci[oó]n))",
            TEXT_FLAGS);

    private static final Pattern INTERNAL_TRANSFER_PATTERN = Pattern.compile(
            "(?:entre\\s+(?:mis|tus)\\s+cuentas|transferencia\\s+propia|transferencia\\s+entre\\s+cuentas\\s+propias)",
            TEXT_FLAGS);

    private static final Pattern OWN_CARD_PAYMENT_PATTERN = Pattern.compile(
            "\\bpago\\s+(?:de\\s+(?:tu\\s+)?tarjeta\\s+(?:de\\s+cr[eé]dito\\s+)?(?:propia|interbank)|a\\s+tu\\s+tarjeta\\s+de\\s+cr[eé]dito)\\b",
            TEXT_FLAGS);

    private static final Pattern PLIN_INDICATOR_PATTERN = Pattern.compile(
            "\\b(?:plin|plineo|plineaste|plinearon|te\\s+pline[oó])\\b",
            TEXT_FLAGS);

    private static final Pattern MERCHANT_LABELED_PATTERN = Pattern.compile(
            "(?:establecimiento|comercio|empresa|servicio|destinatario|beneficiario|destino|a\\s+favor\\s+de):?\\s*([A-Za-z0-9À-ÿ\\s.,&'/*#_+-]+?)(?=\\s*(?:fecha|hora|importe|monto|moneda|tarjeta|cuenta|c[oó]digo|nro|n[uú]mero|operaci[oó]n|$))",
            TEXT_FLAGS);

    private static final Pattern PLIN_SENDER_PATTERN = Pattern.compile(
            "(?:recibiste\\s+un\\s+plin\\s+de\\s+|([A-Za-z0-9À-ÿ\\s.,&'-]+?)\\s+te\\s+(?:pline[oó]|envi[oó]))([A-Za-z0-9À-ÿ\\s.,&'-]+?)?(?=\\s*(?:por|de|con|S/|PEN|\\$|USD|\\.|!|$))",
            TEXT_FLAGS);

    private static final Pattern PLIN_RECIPIENT_PATTERN = Pattern.compile(
            "(?:plineaste\\s+a|enviaste\\s+un\\s+plin\\s+a)\\s+([A-Za-z0-9À-ÿ\\s.,&'-]+?)(?=\\s*(?:por|de|S/|PEN|\\$|USD|\\.|!|$))",
            TEXT_FLAGS);

    private static final Pattern CARD_LAST4_PATTERN = Pattern.compile(
            "(?:terminada\\s+en|tarjeta\\s*\\*+|\\*{3,})\\s*([0-9]{4})",
            TEXT_FLAGS);

    private static final Pattern OP_NUMBER_PATTERN = Pattern.compile(
            "(?:c[oó]digo|nro\\.?|n[uú]mero)\\s*(?:de)?\\s*operaci[oó]n:?\\s*([A-Za-z0-9-]+)",
            TEXT_FLAGS);

    @Override
    public List<String> gmailSearchTerms() {
        return List.of("from:interbank.pe", "from:interbank.com.pe", "from:netinterbank.com.pe");
    }

    @Override
    public boolean supports(String sender, String subject) {
        return BankEmailSenderPolicy.matchesDomain(sender, ALLOWED_DOMAINS);
    }

    @Override
    public String getBankName() {
        return "INTERBANK";
    }

    @Override
    public ParsedEmailTransaction parse(String subject, String htmlOrTextBody, LocalDateTime receivedDate) {
        String cleanText = extractPlainText(htmlOrTextBody);
        if (cleanText.isBlank()) {
            return null;
        }
        String combined = (subject != null ? subject : "") + " " + cleanText;
        if (NON_FINANCIAL_PATTERN.matcher(combined).find()) {
            return null;
        }

        FlowType flowType = determineConfirmedFlow(combined);
        if (flowType == null) {
            return null;
        }

        FlowType identityFlow = flowType;
        boolean ownCardPayment = OWN_CARD_PAYMENT_PATTERN.matcher(combined).find() && flowType != FlowType.INCOME;
        if (ownCardPayment) {
            flowType = FlowType.INTERNAL_TRANSFER;
        }

        ParsedAmount parsedAmount = extractOperationAmount(cleanText);
        if (parsedAmount == null) {
            return null;
        }

        String channel = determineChannel(subject, combined);
        String merchant = extractMerchant(combined, cleanText, flowType, ownCardPayment, channel);
        String cardLast4 = extractCardLast4(combined);
        String operationNumber = extractOperationNumber(cleanText);
        LocalDateTime txDate = receivedDate != null ? receivedDate : LocalDateTime.now();
        String hash = generateHash(identityFlow, parsedAmount.amount(), merchant, operationNumber, txDate);

        return ParsedEmailTransaction.builder()
                .amount(parsedAmount.amount())
                .currency(parsedAmount.currency())
                .flowType(flowType)
                .operationType(ownCardPayment ? OperationType.OWN_CARD_PAYMENT : OperationType.UNKNOWN)
                .merchantName(merchant)
                .channel(channel)
                .cardLast4(cardLast4)
                .operationNumber(operationNumber)
                .receiptReference(operationNumber)
                .transactionDate(txDate)
                .transactionHash(hash)
                .rawBody(cleanText)
                .build();
    }

    private FlowType determineConfirmedFlow(String text) {
        boolean expense = EXPENSE_CONFIRMATION_PATTERN.matcher(text).find();
        boolean income = INCOME_CONFIRMATION_PATTERN.matcher(text).find();
        if (expense == income) {
            return null;
        }
        if (INTERNAL_TRANSFER_PATTERN.matcher(text).find()) {
            return FlowType.INTERNAL_TRANSFER;
        }
        return income ? FlowType.INCOME : FlowType.EXPENSE;
    }

    private ParsedAmount extractOperationAmount(String text) {
        Map<String, ParsedAmount> amounts = new LinkedHashMap<>();
        collectAmounts(LABELED_AMOUNT_PATTERN.matcher(text), amounts);
        collectAmounts(INLINE_AMOUNT_PATTERN.matcher(text), amounts);
        return amounts.size() == 1 ? amounts.values().iterator().next() : null;
    }

    private void collectAmounts(Matcher matcher, Map<String, ParsedAmount> amounts) {
        while (matcher.find()) {
            BigDecimal amount = new BigDecimal(matcher.group(2).replace(",", ""));
            if (amount.signum() <= 0) {
                continue;
            }
            String currency = matcher.group(1).contains("$") || matcher.group(1).equalsIgnoreCase("USD")
                    ? "USD" : "PEN";
            String key = currency + ":" + amount.stripTrailingZeros().toPlainString();
            ParsedAmount existing = amounts.get(key);
            if (existing == null || matcher.start(2) < existing.position()) {
                amounts.put(key, new ParsedAmount(amount, currency, matcher.start(2)));
            }
        }
    }

    private record ParsedAmount(BigDecimal amount, String currency, int position) {
    }

    private String determineChannel(String subject, String combined) {
        String lowerSubject = (subject != null ? subject : "").toLowerCase(Locale.ROOT);
        String lower = combined.toLowerCase(Locale.ROOT);
        if (PLIN_INDICATOR_PATTERN.matcher(combined).find()) {
            return ChannelType.PLIN.name();
        }
        if (lowerSubject.contains("crédito") || lowerSubject.contains("credito")
                || lower.contains("tarjeta de crédito") || lower.contains("tarjeta de credito")) {
            return ChannelType.TARJETA_CREDITO_INTERBANK.name();
        }
        if (lowerSubject.contains("débito") || lowerSubject.contains("debito")
                || lower.contains("tarjeta de débito") || lower.contains("tarjeta de debito")) {
            return ChannelType.TARJETA_DEBITO_INTERBANK.name();
        }
        return ChannelType.INTERBANK_TRANSFERENCIA.name();
    }

    private String extractMerchant(String combined, String cleanText, FlowType flowType, boolean ownCardPayment, String channel) {
        if (ownCardPayment) {
            return "Pago Tarjeta Crédito Interbank";
        }
        if (ChannelType.PLIN.name().equals(channel)) {
            if (flowType == FlowType.INCOME) {
                Matcher sender = PLIN_SENDER_PATTERN.matcher(cleanText);
                if (sender.find()) {
                    String candidate = sender.group(1) != null ? sender.group(1) : sender.group(2);
                    String cleaned = cleanMerchant(candidate);
                    if (cleaned != null) {
                        return cleaned;
                    }
                }
            } else {
                Matcher recipient = PLIN_RECIPIENT_PATTERN.matcher(combined);
                if (recipient.find()) {
                    String cleaned = cleanMerchant(recipient.group(1));
                    if (cleaned != null) {
                        return cleaned;
                    }
                }
            }
        }
        Matcher labeled = MERCHANT_LABELED_PATTERN.matcher(cleanText);
        if (labeled.find()) {
            String cleaned = cleanMerchant(labeled.group(1));
            if (cleaned != null) {
                return cleaned;
            }
        }
        if (ChannelType.PLIN.name().equals(channel)) {
            return flowType == FlowType.INCOME ? "Plin Recibido" : "Plin Enviado";
        }
        return flowType == FlowType.INCOME ? "Abono Interbank" : "Operación Interbank";
    }

    private String cleanMerchant(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll("[.,;:!]+$", "").trim();
        if (cleaned.length() < 3 || cleaned.equalsIgnoreCase("interbank") || cleaned.equalsIgnoreCase("plin")) {
            return null;
        }
        return cleaned;
    }

    private String extractCardLast4(String combined) {
        Matcher cardMatcher = CARD_LAST4_PATTERN.matcher(combined);
        return cardMatcher.find() ? cardMatcher.group(1) : null;
    }

    private String extractOperationNumber(String cleanText) {
        Matcher opMatcher = OP_NUMBER_PATTERN.matcher(cleanText);
        return opMatcher.find() ? opMatcher.group(1) : null;
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

    private String generateHash(FlowType flowType, BigDecimal amount, String merchant, String opNumber, LocalDateTime date) {
        String dateKey = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String opKey = (opNumber != null && !opNumber.isBlank())
                ? opNumber
                : date.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        String input = "INTERBANK_" + flowType.name() + "_" + amount.toPlainString() + "_"
                + merchant.toLowerCase(Locale.ROOT).trim() + "_" + opKey + "_" + dateKey;

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
