package com.financeiro_api.Dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopCategoriaDTO(
        UUID categoriaId,
        String nomeCategoria,
        BigDecimal total
) {}
