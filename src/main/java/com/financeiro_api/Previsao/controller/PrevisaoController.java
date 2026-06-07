package com.financeiro_api.Previsao.controller;

import com.financeiro_api.Previsao.domain.TipoPrevisao;
import com.financeiro_api.Previsao.dto.PrevisaoCreateDTO;
import com.financeiro_api.Previsao.dto.PrevisaoResponseDTO;
import com.financeiro_api.Previsao.service.PrevisaoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Previsões", description = "Planejamento de receitas e despesas futuras")
@RestController
@RequestMapping("/api/v1/previsoes")
public class PrevisaoController {

    private final PrevisaoService service;

    public PrevisaoController(PrevisaoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PrevisaoResponseDTO> listar(
            @RequestParam(required = false) TipoPrevisao tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        size = Math.min(size, 200);
        return service.listarPaginado(tipo, PageRequest.of(page, size, Sort.by("dataInicio").descending()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrevisaoResponseDTO criar(@RequestBody @Valid PrevisaoCreateDTO dto) {
        return service.criar(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable UUID id) {
        service.desativar(id);
    }
}
