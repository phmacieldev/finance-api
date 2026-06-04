package com.financeiro_api.Previsao.dto;

import com.financeiro_api.Previsao.domain.Frequencia;
import com.financeiro_api.Previsao.domain.TipoPrevisao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PrevisaoCreateDTO(
        @NotBlank String descricao,
        @NotNull TipoPrevisao tipo,
        @NotNull @Positive BigDecimal valor,
        @NotNull Frequencia frequencia,
        @NotNull LocalDate dataInicio,
        LocalDate dataFim,
        Integer diaRecorrencia,
        UUID categoriaId
) {}
