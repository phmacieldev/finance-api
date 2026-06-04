package com.financeiro_api.Dre.controller;

import com.financeiro_api.Dre.dto.DreResponseDTO;
import com.financeiro_api.Dre.service.DreService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dre")
public class DreController {

    private final DreService service;

    public DreController(DreService service) {
        this.service = service;
    }

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
