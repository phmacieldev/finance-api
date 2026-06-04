package com.financeiro_api.shared.dto;

import java.time.LocalDateTime;

public record ErroResponse(
        int status,
        String message,
        LocalDateTime timestamp
) {
    public ErroResponse(int status, String message) {
        this(status, message, LocalDateTime.now());
    }
}
