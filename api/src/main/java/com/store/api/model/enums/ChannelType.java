package com.store.api.model.enums;

import java.util.Locale;

public enum ChannelType {
    YAPE("Yape", Kind.WALLET),
    PLIN("Plin", Kind.WALLET),
    TARJETA_CREDITO_BCP("Crédito BCP", Kind.CREDIT_CARD),
    TARJETA_DEBITO_BCP("Débito BCP", Kind.DEBIT_CARD),
    BCP_TRANSFERENCIA("Transferencia BCP", Kind.TRANSFER),
    TARJETA_CREDITO_INTERBANK("Crédito Interbank", Kind.CREDIT_CARD),
    TARJETA_DEBITO_INTERBANK("Débito Interbank", Kind.DEBIT_CARD),
    TARJETA_CREDITO_BBVA("Crédito BBVA", Kind.CREDIT_CARD),
    TARJETA_DEBITO_BBVA("Débito BBVA", Kind.DEBIT_CARD),
    CASH("Efectivo", Kind.CASH),
    CARD("Tarjeta", Kind.CARD),
    BANK_TRANSFER("Transferencia", Kind.TRANSFER),
    WALLET("Billetera", Kind.WALLET),
    OTHER("Otro", Kind.OTHER),
    OTRO("Otro", Kind.OTHER),
    UNKNOWN("Desconocido", Kind.OTHER);

    public enum Kind { CREDIT_CARD, DEBIT_CARD, CARD, WALLET, TRANSFER, CASH, OTHER }

    public record Presentation(String code, String label, Kind kind) {
        public String displayName(String cardLast4) {
            return cardLast4 == null || cardLast4.isBlank() ? label : label + " **" + cardLast4;
        }
    }

    private final String label;
    private final Kind kind;

    ChannelType(String label, Kind kind) {
        this.label = label;
        this.kind = kind;
    }

    public static Presentation describe(String channel) {
        String code = channel == null || channel.isBlank() ? "UNKNOWN" : channel.trim().toUpperCase(Locale.ROOT);
        try {
            ChannelType known = valueOf(code);
            return new Presentation(code, known.label, known.kind);
        } catch (IllegalArgumentException unknown) {
            return new Presentation(code, channel.trim().replace('_', ' '), Kind.OTHER);
        }
    }

    public static String formatDisplayName(String channel, String cardLast4) {
        return describe(channel).displayName(cardLast4);
    }
}
