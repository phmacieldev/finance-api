package com.financeiro_api.Relatorio.controller;

import com.financeiro_api.Relatorio.dto.RelatorioMensalDTO;
import com.financeiro_api.Relatorio.service.RelatorioService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/relatorio")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    @GetMapping("/mensal")
    public RelatorioMensalDTO mensal(@RequestParam(defaultValue = "6") int meses) {
        if (meses < 1) meses = 1;
        if (meses > 24) meses = 24;
        return service.gerarRelatorioMensal(meses);
    }
}
