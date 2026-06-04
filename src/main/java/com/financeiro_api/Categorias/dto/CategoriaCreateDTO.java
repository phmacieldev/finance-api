package com.financeiro_api.Categorias.dto;

import com.financeiro_api.Categorias.domain.DreCategoria;
import com.financeiro_api.Categorias.domain.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CategoriaCreateDTO(
        @NotBlank String name,
        @NotNull TipoCategoria tipo,
        DreCategoria dreCategoria
) {}
