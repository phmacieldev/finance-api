package com.financeiro_api.Dashboard.controller;

import com.financeiro_api.Dashboard.dto.DashboardDTO;
import com.financeiro_api.Dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Dashboard", description = "Resumo financeiro do mês com totais, variações e alertas")
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @Operation(summary = "Calcular dashboard do mês (padrão: mês atual)")
    @GetMapping
    public DashboardDTO calcular(
            @RequestParam(defaultValue = "0") int mes,
            @RequestParam(defaultValue = "0") int ano) {
        if (mes == 0 || ano == 0) {
            LocalDate hoje = LocalDate.now();
            return service.calcular(hoje.getMonthValue(), hoje.getYear());
        }
        return service.calcular(mes, ano);
    }
}
