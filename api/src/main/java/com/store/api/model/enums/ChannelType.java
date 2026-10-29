package com.store.api.model.enums;

public enum ChannelType {
    YAPE,
    PLIN,
    TARJETA_CREDITO_BCP,
    TARJETA_DEBITO_BCP,
    BCP_TRANSFERENCIA,
    TARJETA_CREDITO_INTERBANK,
    TARJETA_DEBITO_INTERBANK,
    TARJETA_CREDITO_BBVA,
    TARJETA_DEBITO_BBVA,
    OTRO;

    public static String formatDisplayName(String channel, String cardLast4) {
        if (channel != null) {
            String generic = switch (channel) {
                case "CASH" -> "Efectivo";
                case "CARD" -> "Tarjeta";
                case "BANK_TRANSFER" -> "Transferencia";
                case "WALLET" -> "Billetera";
                case "OTHER" -> "Otro";
                default -> null;
            };
            if (generic != null) return generic;
        }
        if (cardLast4 != null && !cardLast4.isBlank()) {
            if (channel != null && channel.contains("CREDITO")) {
                return "BCP Crédito **" + cardLast4;
            } else if (channel != null && channel.contains("DEBITO")) {
                return "BCP Débito **" + cardLast4;
            } else if (channel != null && (channel.contains("TRANSFERENCIA") || channel.contains("AHORRO"))) {
                return "BCP Cuenta **" + cardLast4;
            } else {
                return "BCP **" + cardLast4;
            }
        } else if (channel != null && channel.contains("YAPE")) {
            return "Yape";
        } else if (channel != null && channel.contains("PLIN")) {
            return "Plin";
        } else if (channel != null) {
            return channel.replace("_", " ");
        } else {
            return "Desconocido";
        }
    }
}
