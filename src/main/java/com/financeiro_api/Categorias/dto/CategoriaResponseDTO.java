package com.financeiro_api.Categorias.dto;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.domain.DreCategoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;

import java.util.UUID;

public record CategoriaResponseDTO(
        UUID id,
        String name,
        TipoCategoria tipo,
        DreCategoria dreCategoria
) {
    public static CategoriaResponseDTO from(Categoria c) {
        return new CategoriaResponseDTO(c.getId(), c.getName(), c.getTipo(), c.getDreCategoria());
    }
}
