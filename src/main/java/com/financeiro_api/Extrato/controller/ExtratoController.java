package com.financeiro_api.Extrato.controller;

import com.financeiro_api.Extrato.dto.AtribuirCategoriaDTO;
import com.financeiro_api.Extrato.dto.AtribuirContaDTO;
import com.financeiro_api.Extrato.dto.CriarExtratoDTO;
import com.financeiro_api.Extrato.dto.EditarExtratoDTO;
import com.financeiro_api.Extrato.dto.ExtratoImportResultDTO;
import com.financeiro_api.Extrato.dto.ExtratoResponseDTO;
import com.financeiro_api.Extrato.service.CsvImportService;
import com.financeiro_api.Extrato.service.ExtratoService;
import com.financeiro_api.Extrato.service.XlsxImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Extratos", description = "Import CSV/XLSX, listagem paginada e classificação de lançamentos")
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

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "text/csv", "text/plain", "application/csv",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    @Operation(summary = "Importar extrato (CSV ou XLSX — auto-detectado pelo nome do arquivo)")
    @PostMapping("/importar")
    @ResponseStatus(HttpStatus.CREATED)
    public ExtratoImportResultDTO importar(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID contaBancariaId) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase() : "";
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de arquivo não permitido. Envie um CSV ou XLSX.");
        }
        String nome = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        if (nome.endsWith(".xlsx") || nome.endsWith(".xls")) {
            return xlsxImportService.importar(file, contaBancariaId);
        }
        return csvImportService.importar(file, contaBancariaId);
    }

    @Operation(summary = "Criar lançamento manual")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExtratoResponseDTO criar(@RequestBody @Valid CriarExtratoDTO dto) {
        return service.criarManual(dto);
    }

    @Operation(summary = "Editar lançamento")
    @PatchMapping("/{id}")
    public ExtratoResponseDTO editar(@PathVariable UUID id,
                                      @RequestBody EditarExtratoDTO dto) {
        return service.editar(id, dto);
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
