package com.financeiro_api.Categorias.controller;

import com.financeiro_api.Categorias.domain.TipoCategoria;
import com.financeiro_api.Categorias.dto.CategoriaCreateDTO;
import com.financeiro_api.Categorias.dto.CategoriaResponseDTO;
import com.financeiro_api.Categorias.service.CategoriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private final CategoriaService service;

    public CategoriaController(CategoriaService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoriaResponseDTO> listar(@RequestParam(required = false) TipoCategoria tipo) {
        if (tipo != null) return service.listarPorTipo(tipo);
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponseDTO criar(@RequestBody @Valid CategoriaCreateDTO dto) {
        return service.criar(dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable UUID id) {
        service.deletar(id);
    }
}
