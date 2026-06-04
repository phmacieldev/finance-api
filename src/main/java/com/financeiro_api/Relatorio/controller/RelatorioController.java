package com.financeiro_api.Relatorio.controller;

import com.financeiro_api.Relatorio.dto.RelatorioMensalDTO;
import com.financeiro_api.Relatorio.service.RelatorioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Relatório", description = "Série histórica mensal de receitas, despesas e saldo")
@RestController
@RequestMapping("/api/v1/relatorio")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @Operation(summary = "Relatório mensal agregado (padrão: últimos 6 meses, máx. 24)")
    @GetMapping("/mensal")
    public RelatorioMensalDTO mensal(@RequestParam(defaultValue = "6") int meses) {
        if (meses < 1) meses = 1;
        if (meses > 24) meses = 24;
        return service.gerarRelatorioMensal(meses);
    }
}
