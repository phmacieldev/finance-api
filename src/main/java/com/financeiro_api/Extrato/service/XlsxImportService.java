package com.financeiro_api.Extrato.service;

import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.dto.ExtratoImportResultDTO;
import com.financeiro_api.shared.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class XlsxImportService {

    private final ExtratoImportHelper helper;

    public XlsxImportService(ExtratoImportHelper helper) {
        this.helper = helper;
    }

    @Transactional
    public ExtratoImportResultDTO importar(MultipartFile file) {
        return importar(file, null);
    }

    @Transactional
    public ExtratoImportResultDTO importar(MultipartFile file, UUID contaBancariaId) {
        UUID tenantId = TenantContext.get();
        List<Extrato> extratos = new ArrayList<>();
        int erros = 0;

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            // Localiza o cabeçalho real (ignora metadados do banco)
            int headerRowIdx = encontrarHeaderRow(sheet);
            if (headerRowIdx < 0) {
                throw new IllegalArgumentException(
                        "Cabeçalho não encontrado. Verifique se a planilha possui as colunas: Data, Valor.");
            }

            Map<String, Integer> colunas = mapearColunas(sheet.getRow(headerRowIdx));

            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowVazia(row)) continue;

                try {
                    // Pula linhas de saldo/resumo
                    String lancamento = getCellString(row, colunas.get("tipo_pagamento"));
                    if (helper.deveIgnorar(lancamento)) continue;

                    // Pula linhas sem valor de transação
                    BigDecimal valor = getCellDecimal(row, colunas.get("valor"));
                    if (valor == null) continue;

                    LocalDate data = getCellData(row, colunas.get("data"));
                    if (data == null) continue;

                    String razaoSocial = getCellString(row, colunas.get("razao_social"));
                    String cpfCnpj = getCellString(row, colunas.get("cpf_cnpj"));
                    BigDecimal saldo = getCellDecimal(row, colunas.get("saldo"));

                    extratos.add(helper.construirExtrato(tenantId, data, lancamento,
                            razaoSocial, cpfCnpj, valor, saldo, contaBancariaId));

                } catch (Exception e) {
                    erros++;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao processar planilha: " + e.getMessage());
        }

        ExtratoImportResultDTO resultado = helper.salvarLote(extratos, tenantId);
        int totalErros = resultado.erros() + erros;
        return new ExtratoImportResultDTO(
                resultado.importados(),
                resultado.duplicatasIgnoradas(),
                totalErros,
                resultado.batchId(),
                String.format("Importados: %d | Duplicatas ignoradas: %d | Erros: %d",
                        resultado.importados(), resultado.duplicatasIgnoradas(), totalErros)
        );
    }

    private int encontrarHeaderRow(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            // Procura linha onde o primeiro campo é "data" e tem campo "valor"
            String primeiro = helper.normalizarHeader(getCellString(row, 0));
            if (!primeiro.equals("data") && !primeiro.startsWith("data_")) continue;

            // Verifica se tem coluna de valor na mesma linha
            for (int j = 0; j < row.getLastCellNum(); j++) {
                String h = helper.normalizarHeader(getCellString(row, j));
                if (h.startsWith("valor")) return i;
            }
        }
        return -1;
    }

    private Map<String, Integer> mapearColunas(Row headerRow) {
        Map<String, Integer> mapa = new HashMap<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String h = helper.normalizarHeader(getCellString(headerRow, i));

            if (!mapa.containsKey("data") && (h.equals("data") || h.startsWith("data_"))) {
                mapa.put("data", i);
            } else if (!mapa.containsKey("tipo_pagamento") && (
                    h.startsWith("lancamento") || h.equals("historico") ||
                    h.startsWith("descricao") || h.equals("tipo") ||
                    h.startsWith("tipo_pag") || h.startsWith("forma"))) {
                mapa.put("tipo_pagamento", i);
            } else if (!mapa.containsKey("razao_social") && (
                    h.contains("razao") || h.equals("nome") ||
                    h.startsWith("beneficiario") || h.startsWith("favorecido"))) {
                mapa.put("razao_social", i);
            } else if (!mapa.containsKey("cpf_cnpj") && (h.contains("cpf") || h.contains("cnpj"))) {
                mapa.put("cpf_cnpj", i);
            } else if (!mapa.containsKey("valor") && h.startsWith("valor")) {
                mapa.put("valor", i);
            } else if (!mapa.containsKey("saldo") && h.startsWith("saldo")) {
                mapa.put("saldo", i);
            }
        }

        // Fallback posicional (7 colunas: Data, Lançamento, Ag/origem, Razão Social, CPF/CNPJ, Valor, Saldo)
        if (!mapa.containsKey("data") || !mapa.containsKey("valor")) {
            mapa.clear();
            mapa.put("data", 0);
            mapa.put("tipo_pagamento", 1);
            mapa.put("razao_social", 3);
            mapa.put("cpf_cnpj", 4);
            mapa.put("valor", 5);
            mapa.put("saldo", 6);
        }

        return mapa;
    }

    private String getCellString(Row row, Integer colIdx) {
        if (row == null || colIdx == null) return null;
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Data formatada — retorna como string dd/MM/yyyy
                    yield String.format("%td/%tm/%tY",
                            cell.getLocalDateTimeCellValue(),
                            cell.getLocalDateTimeCellValue(),
                            cell.getLocalDateTimeCellValue());
                }
                // Número: retira decimais desnecessários
                double v = cell.getNumericCellValue();
                yield (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
            }
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue(); }
            }
            default -> null;
        };
    }

    private BigDecimal getCellDecimal(Row row, Integer colIdx) {
        if (row == null || colIdx == null) return null;
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> helper.parsarDecimalBrasileiro(cell.getStringCellValue());
            case FORMULA -> {
                try { yield BigDecimal.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { yield null; }
            }
            default -> null;
        };
    }

    private LocalDate getCellData(Row row, Integer colIdx) {
        if (row == null || colIdx == null) return null;
        Cell cell = row.getCell(colIdx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue()
                    .toLocalDate();
        }

        String str = getCellString(row, colIdx);
        if (str == null) return null;
        try { return helper.parseData(str); } catch (Exception e) { return null; }
    }

    private boolean isRowVazia(Row row) {
        if (row == null) return true;
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(row, i);
                if (val != null && !val.isBlank()) return false;
            }
        }
        return true;
    }
}
