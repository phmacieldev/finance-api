package com.financeiro_api.SaldoAnterior.dto;

import com.financeiro_api.SaldoAnterior.domain.SaldoAnterior;

import java.math.BigDecimal;
import java.util.UUID;

public record SaldoAnteriorResponseDTO(
        UUID id,
        int mes,
        int ano,
        BigDecimal valor
) {
    public static SaldoAnteriorResponseDTO from(SaldoAnterior s) {
        return new SaldoAnteriorResponseDTO(s.getId(), s.getMes(), s.getAno(), s.getValor());
    }

    public static SaldoAnteriorResponseDTO zero(int mes, int ano) {
        return new SaldoAnteriorResponseDTO(null, mes, ano, BigDecimal.ZERO);
    }
}
