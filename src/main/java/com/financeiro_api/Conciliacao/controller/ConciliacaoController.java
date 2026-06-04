package com.financeiro_api.Conciliacao.controller;

import com.financeiro_api.Conciliacao.dto.ConciliacaoPeriodoDTO;
import com.financeiro_api.Conciliacao.service.ConciliacaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Conciliação", description = "Conciliação bancária mensal e por período")
@RestController
@RequestMapping("/api/v1/conciliacao")
public class ConciliacaoController {

    private final ConciliacaoService service;

    public ConciliacaoController(ConciliacaoService service) {
        this.service = service;
    }

    @Operation(summary = "Conciliar mês completo")
    @GetMapping("/mensal")
    public ConciliacaoPeriodoDTO mensal(
            @RequestParam int mes,
            @RequestParam int ano) {
        return service.conciliarMes(mes, ano);
    }

    @Operation(summary = "Conciliar intervalo de datas")
    @GetMapping("/periodo")
    public ConciliacaoPeriodoDTO periodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.conciliarPeriodo(inicio, fim);
    }
}
