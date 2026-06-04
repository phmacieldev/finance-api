package com.financeiro_api.ContaBancaria.controller;

import com.financeiro_api.ContaBancaria.dto.ContaBancariaCreateDTO;
import com.financeiro_api.ContaBancaria.dto.ContaBancariaResponseDTO;
import com.financeiro_api.ContaBancaria.service.ContaBancariaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contas-bancarias")
public class ContaBancariaController {

    private final ContaBancariaService service;

    public ContaBancariaController(ContaBancariaService service) {
        this.service = service;
    }

    @GetMapping
    public List<ContaBancariaResponseDTO> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaBancariaResponseDTO criar(@RequestBody ContaBancariaCreateDTO dto) {
        return service.criar(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable UUID id) {
        service.desativar(id);
    }
}
