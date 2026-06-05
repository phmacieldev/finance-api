package com.financeiro_api.shared.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ErroResponse(
        int status,
        String message,
        LocalDateTime timestamp,
        Map<String, String> campos
) {
    public ErroResponse(int status, String message) {
        this(status, message, LocalDateTime.now(), null);
    }

    public ErroResponse(int status, String message, Map<String, String> campos) {
        this(status, message, LocalDateTime.now(), campos);
    }
}
