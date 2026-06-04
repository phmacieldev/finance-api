package com.financeiro_api.Extrato.dto;

public record ExtratoImportResultDTO(
        int importados,
        int duplicatasIgnoradas,
        int erros,
        String batchId,
        String mensagem
) {}
