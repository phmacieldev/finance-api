package com.financeiro_api.Extrato.dto;

import com.financeiro_api.Extrato.domain.Extrato;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExtratoResponseDTO(
        UUID id,
        LocalDate data,
        String tipoPagamento,
        String razaoSocial,
        String cpfCnpj,
        BigDecimal valor,
        BigDecimal saldo,
        UUID categoriaId,
        UUID contaBancariaId,
        boolean conciliado,
        int mes,
        int ano
) {
    public static ExtratoResponseDTO from(Extrato e) {
        return new ExtratoResponseDTO(
                e.getId(), e.getData(), e.getTipoPagamento(),
                e.getRazaoSocial(), e.getCpfCnpj(), e.getValor(),
                e.getSaldo(), e.getCategoriaId(), e.getContaBancariaId(),
                e.isConciliado(), e.getMes(), e.getAno()
        );
    }
}
