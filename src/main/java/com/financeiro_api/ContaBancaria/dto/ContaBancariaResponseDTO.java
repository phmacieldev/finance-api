package com.financeiro_api.ContaBancaria.dto;

import com.financeiro_api.ContaBancaria.domain.ContaBancaria;
import com.financeiro_api.ContaBancaria.domain.TipoConta;

import java.util.UUID;

public record ContaBancariaResponseDTO(
        UUID id,
        String nome,
        String banco,
        TipoConta tipo,
        boolean ativa
) {
    public static ContaBancariaResponseDTO from(ContaBancaria c) {
        return new ContaBancariaResponseDTO(c.getId(), c.getNome(), c.getBanco(), c.getTipo(), c.isAtiva());
    }
}
