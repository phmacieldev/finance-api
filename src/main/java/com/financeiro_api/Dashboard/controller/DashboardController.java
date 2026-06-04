package com.financeiro_api.Dashboard.controller;

import com.financeiro_api.Dashboard.dto.DashboardDTO;
import com.financeiro_api.Dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

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
