package com.financeiro_api.ContaBancaria.dto;

import com.financeiro_api.ContaBancaria.domain.TipoConta;

public record ContaBancariaCreateDTO(
        String nome,
        String banco,
        TipoConta tipo
) {}
