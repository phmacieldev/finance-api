package com.financeiro_api.shared;

public final class LogMask {

    private LogMask() {}

    // "user@example.com" → "us***@example.com"
    public static String email(String email) {
        if (email == null) return "[null]";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.substring(0, 2) + "***" + domain;
    }

    // "11222333000181" → "11***333***81"
    public static String cnpj(String cnpj) {
        if (cnpj == null) return "[null]";
        String digits = cnpj.replaceAll("[^\\d]", "");
        if (digits.length() != 14) return "***";
        return digits.substring(0, 2) + "***" + digits.substring(5, 8) + "***" + digits.substring(12);
    }

    // Shows first 8 chars only — enough to correlate logs, not enough to replay
    public static String token(String token) {
        if (token == null) return "[null]";
        if (token.length() <= 8) return "***";
        return token.substring(0, 8) + "...";
    }

    // Redacts email addresses and CNPJs found inside arbitrary strings (e.g. exception messages)
    public static String sanitize(String message) {
        if (message == null) return "[null]";
        return message
                .replaceAll("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}", "[email]")
                .replaceAll("\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}", "[cnpj]");
    }
}
