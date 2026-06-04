package com.financeiro_api.Auth.dto;

public record TokenResponseDTO(
        String token,
        String type,
        String refreshToken,
        String email,
        String role,
        boolean emailPendente
) {
    public TokenResponseDTO(String token, String refreshToken, String email, String role) {
        this(token, "Bearer", refreshToken, email, role, false);
    }

    public TokenResponseDTO(String email, String role) {
        this(null, null, null, email, role, true);
    }
}
