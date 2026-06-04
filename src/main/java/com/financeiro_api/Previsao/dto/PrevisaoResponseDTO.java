package com.financeiro_api.Previsao.dto;

import com.financeiro_api.Previsao.domain.Frequencia;
import com.financeiro_api.Previsao.domain.Previsao;
import com.financeiro_api.Previsao.domain.TipoPrevisao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PrevisaoResponseDTO(
        UUID id,
        String descricao,
        TipoPrevisao tipo,
        BigDecimal valor,
        Frequencia frequencia,
        LocalDate dataInicio,
        LocalDate dataFim,
        Integer diaRecorrencia,
        UUID categoriaId,
        boolean ativa
) {
    public static PrevisaoResponseDTO from(Previsao p) {
        return new PrevisaoResponseDTO(
                p.getId(), p.getDescricao(), p.getTipo(), p.getValor(),
                p.getFrequencia(), p.getDataInicio(), p.getDataFim(),
                p.getDiaRecorrencia(), p.getCategoriaId(), p.isAtiva()
        );
    }
}
