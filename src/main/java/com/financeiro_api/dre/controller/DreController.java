package com.financeiro_api.Dre.controller;

import com.financeiro_api.Dre.dto.DreResponseDTO;
import com.financeiro_api.Dre.service.DreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "DRE", description = "Demonstração do Resultado do Exercício agrupada por categoria")
@RestController
@RequestMapping("/api/v1/dre")
public class DreController {

    private final DreService service;

    public DreController(DreService service) {
        this.service = service;
    }

    @Operation(summary = "Calcular DRE do mês (padrão: mês atual)")
    @GetMapping
    public DreResponseDTO calcular(
            @RequestParam(defaultValue = "0") int mes,
            @RequestParam(defaultValue = "0") int ano) {
        if (mes == 0 || ano == 0) {
            LocalDate hoje = LocalDate.now();
            return service.calcular(hoje.getMonthValue(), hoje.getYear());
        }
        return service.calcular(mes, ano);
    }
}
