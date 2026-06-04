package com.financeiro_api.SaldoAnterior.controller;

import com.financeiro_api.SaldoAnterior.dto.SaldoAnteriorDTO;
import com.financeiro_api.SaldoAnterior.dto.SaldoAnteriorResponseDTO;
import com.financeiro_api.SaldoAnterior.service.SaldoAnteriorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/saldo-anterior")
public class SaldoAnteriorController {

    private final SaldoAnteriorService service;

    public SaldoAnteriorController(SaldoAnteriorService service) {
        this.service = service;
    }

    @GetMapping
    public SaldoAnteriorResponseDTO buscar(
            @RequestParam int mes,
            @RequestParam int ano) {
        return service.buscar(mes, ano);
    }

    @PostMapping
    public SaldoAnteriorResponseDTO salvar(@RequestBody @Valid SaldoAnteriorDTO dto) {
        return service.salvar(dto);
    }
}
