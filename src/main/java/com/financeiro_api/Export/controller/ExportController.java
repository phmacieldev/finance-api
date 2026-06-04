package com.financeiro_api.Export.controller;

import com.financeiro_api.Export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "Export", description = "Download do extrato em CSV ou XLSX")
@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final ExportService service;

    public ExportController(ExportService service) {
        this.service = service;
    }

    @Operation(summary = "Exportar extrato do mês em CSV")
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportarCsv(
            @RequestParam(defaultValue = "0") int mes,
            @RequestParam(defaultValue = "0") int ano) {

        if (mes == 0 || ano == 0) {
            LocalDate hoje = LocalDate.now();
            mes = hoje.getMonthValue();
            ano = hoje.getYear();
        }

        byte[] conteudo = service.exportarCsv(mes, ano);
        String nomeArquivo = "extrato_" + String.format("%02d", mes) + "_" + ano + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(conteudo);
    }

    @Operation(summary = "Exportar relatório do mês em XLSX")
    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> exportarXlsx(
            @RequestParam(defaultValue = "0") int mes,
            @RequestParam(defaultValue = "0") int ano) {

        if (mes == 0 || ano == 0) {
            LocalDate hoje = LocalDate.now();
            mes = hoje.getMonthValue();
            ano = hoje.getYear();
        }

        byte[] conteudo = service.exportarXlsx(mes, ano);
        String nomeArquivo = "relatorio_" + String.format("%02d", mes) + "_" + ano + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(conteudo);
    }
}
