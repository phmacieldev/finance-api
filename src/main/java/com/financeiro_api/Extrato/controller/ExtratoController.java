package com.financeiro_api.Extrato.controller;

import com.financeiro_api.Extrato.dto.AtribuirCategoriaDTO;
import com.financeiro_api.Extrato.dto.AtribuirContaDTO;
import com.financeiro_api.Extrato.dto.ExtratoImportResultDTO;
import com.financeiro_api.Extrato.dto.ExtratoResponseDTO;
import com.financeiro_api.Extrato.service.CsvImportService;
import com.financeiro_api.Extrato.service.ExtratoService;
import com.financeiro_api.Extrato.service.XlsxImportService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/extratos")
public class ExtratoController {

    private final ExtratoService service;
    private final CsvImportService csvImportService;
    private final XlsxImportService xlsxImportService;

    public ExtratoController(ExtratoService service,
                             CsvImportService csvImportService,
                             XlsxImportService xlsxImportService) {
        this.service = service;
        this.csvImportService = csvImportService;
        this.xlsxImportService = xlsxImportService;
    }

    @PostMapping("/importar")
    @ResponseStatus(HttpStatus.CREATED)
    public ExtratoImportResultDTO importar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID contaBancariaId) {
        String nome = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";

        if (nome.endsWith(".xlsx") || nome.endsWith(".xls")) {
            return xlsxImportService.importar(file, contaBancariaId);
        }
        return csvImportService.importar(file, contaBancariaId);
    }

    @GetMapping
    public Page<ExtratoResponseDTO> listar(
            @RequestParam(required = false) Integer mes,
            @RequestParam(required = false) Integer ano,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) String razaoSocial,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) UUID contaBancariaId,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (inicio != null && fim != null) {
            List<ExtratoResponseDTO> items = razaoSocial != null
                    ? service.buscarComFiltro(inicio, fim, razaoSocial)
                    : service.listarPorPeriodo(inicio, fim);
            int start = Math.min(page * size, items.size());
            int end = Math.min(start + size, items.size());
            return new org.springframework.data.domain.PageImpl<>(
                    items.subList(start, end),
                    PageRequest.of(page, size),
                    items.size());
        }

        int m = mes != null ? mes : LocalDate.now().getMonthValue();
        int a = ano != null ? ano : LocalDate.now().getYear();
        return service.listarPaginado(m, a, categoriaId, contaBancariaId, tipo,
                PageRequest.of(page, size, Sort.by("data").ascending()));
    }

    @GetMapping("/sem-categoria")
    public List<ExtratoResponseDTO> semCategoria(
            @RequestParam int mes,
            @RequestParam int ano) {
        return service.listarSemCategoria(mes, ano);
    }

    @PatchMapping("/{id}/categoria")
    public ExtratoResponseDTO atribuirCategoria(@PathVariable UUID id,
                                                @RequestBody @Valid AtribuirCategoriaDTO dto) {
        return service.atribuirCategoria(id, dto);
    }

    @PatchMapping("/{id}/conta-bancaria")
    public ExtratoResponseDTO atribuirConta(@PathVariable UUID id,
                                            @RequestBody AtribuirContaDTO dto) {
        return service.atribuirConta(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable UUID id) {
        service.deletar(id);
    }

    @DeleteMapping("/batch/{batchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarLote(@PathVariable UUID batchId) {
        service.cancelarLote(batchId);
    }
}
