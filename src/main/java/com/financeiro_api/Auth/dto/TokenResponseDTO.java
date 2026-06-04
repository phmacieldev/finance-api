package com.financeiro_api.Auth.dto;

public record TokenResponseDTO(
        String token,
        String type,
        String email,
        String role,
        boolean emailPendente
) {
    public TokenResponseDTO(String token, String email, String role) {
        this(token, token != null ? "Bearer" : null, email, role, token == null);
    }
}
