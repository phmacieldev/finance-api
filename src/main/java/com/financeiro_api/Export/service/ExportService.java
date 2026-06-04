package com.financeiro_api.Export.service;

import com.financeiro_api.Categorias.domain.Categoria;
import com.financeiro_api.Categorias.repository.CategoriaRepository;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import com.financeiro_api.shared.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExtratoRepository extratoRepository;
    private final CategoriaRepository categoriaRepository;

    public ExportService(ExtratoRepository extratoRepository, CategoriaRepository categoriaRepository) {
        this.extratoRepository = extratoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    public byte[] exportarCsv(int mes, int ano) {
        UUID tenantId = TenantContext.get();
        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(tenantId, mes, ano);

        Map<UUID, Categoria> categorias = categoriaRepository.findAllByEnterpriseId(tenantId)
                .stream().collect(Collectors.toMap(Categoria::getId, c -> c));

        StringBuilder sb = new StringBuilder();
        sb.append("Data;Tipo Pagamento;Razão Social;CPF/CNPJ;Valor;Saldo;Categoria;Conciliado\n");

        for (Extrato e : extratos) {
            String cat = (e.getCategoriaId() != null && categorias.containsKey(e.getCategoriaId()))
                    ? categorias.get(e.getCategoriaId()).getName() : "";
            sb.append(String.join(";",
                    e.getData().format(BR_DATE),
                    nvl(e.getTipoPagamento()),
                    nvl(e.getRazaoSocial()),
                    nvl(e.getCpfCnpj()),
                    formatarDecimal(e.getValor()),
                    e.getSaldo() != null ? formatarDecimal(e.getSaldo()) : "",
                    cat,
                    e.isConciliado() ? "Sim" : "Não"
            )).append("\n");
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] exportarXlsx(int mes, int ano) {
        UUID tenantId = TenantContext.get();
        List<Extrato> extratos = extratoRepository
                .findAllByEnterpriseIdAndMesAndAnoOrderByDataAsc(tenantId, mes, ano);

        Map<UUID, Categoria> categorias = categoriaRepository.findAllByEnterpriseId(tenantId)
                .stream().collect(Collectors.toMap(Categoria::getId, c -> c));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = criarEstiloHeader(workbook);
            CellStyle moedaStyle = criarEstiloMoeda(workbook);
            CellStyle subtotalStyle = criarEstiloSubtotal(workbook);

            criarAbaDetalhe(workbook, extratos, categorias, headerStyle, moedaStyle);
            criarAbaResumo(workbook, extratos, categorias, headerStyle, moedaStyle, subtotalStyle, mes, ano);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erro ao gerar XLSX: " + e.getMessage());
        }
    }

    private void criarAbaDetalhe(Workbook wb, List<Extrato> extratos, Map<UUID, Categoria> cats,
                                  CellStyle headerStyle, CellStyle moedaStyle) {
        Sheet sheet = wb.createSheet("Extrato Detalhado");
        String[] headers = {"Data", "Tipo Pagamento", "Razão Social", "CPF/CNPJ", "Valor", "Saldo", "Categoria", "Conciliado"};
        criarLinhaCabecalho(sheet, headers, headerStyle);

        int rowNum = 1;
        for (Extrato e : extratos) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(e.getData().format(BR_DATE));
            row.createCell(1).setCellValue(nvl(e.getTipoPagamento()));
            row.createCell(2).setCellValue(nvl(e.getRazaoSocial()));
            row.createCell(3).setCellValue(nvl(e.getCpfCnpj()));
            Cell valorCell = row.createCell(4);
            valorCell.setCellValue(e.getValor().doubleValue());
            valorCell.setCellStyle(moedaStyle);
            if (e.getSaldo() != null) {
                Cell saldoCell = row.createCell(5);
                saldoCell.setCellValue(e.getSaldo().doubleValue());
                saldoCell.setCellStyle(moedaStyle);
            }
            String cat = (e.getCategoriaId() != null && cats.containsKey(e.getCategoriaId()))
                    ? cats.get(e.getCategoriaId()).getName() : "";
            row.createCell(6).setCellValue(cat);
            row.createCell(7).setCellValue(e.isConciliado() ? "Sim" : "Não");
        }
        autoSizeColunas(sheet, 8);
    }

    private void criarAbaResumo(Workbook wb, List<Extrato> extratos, Map<UUID, Categoria> cats,
                                 CellStyle headerStyle, CellStyle moedaStyle, CellStyle subtotalStyle,
                                 int mes, int ano) {
        Sheet sheet = wb.createSheet("Resumo Mensal");
        String nomeMes = YearMonth.of(ano, mes).getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, new Locale("pt", "BR"));

        Row titulo = sheet.createRow(0);
        Cell tituloCell = titulo.createCell(0);
        tituloCell.setCellValue("Relatório Financeiro — " + nomeMes + "/" + ano);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        BigDecimal totalEntradas = extratos.stream()
                .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) > 0)
                .map(Extrato::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSaidas = extratos.stream()
                .filter(e -> e.getValor().compareTo(BigDecimal.ZERO) < 0)
                .map(e -> e.getValor().abs()).reduce(BigDecimal.ZERO, BigDecimal::add);

        int r = 2;
        adicionarLinhaResumo(sheet, r++, "Total Entradas", totalEntradas, moedaStyle);
        adicionarLinhaResumo(sheet, r++, "Total Saídas", totalSaidas, moedaStyle);
        adicionarLinhaResumo(sheet, r++, "Saldo do Mês", totalEntradas.subtract(totalSaidas), subtotalStyle);
        r++;
        adicionarLinhaResumo(sheet, r++, "Transações no mês", BigDecimal.valueOf(extratos.size()), null);
        long semCat = extratos.stream().filter(e -> e.getCategoriaId() == null).count();
        adicionarLinhaResumo(sheet, r, "Sem categoria", BigDecimal.valueOf(semCat), null);

        autoSizeColunas(sheet, 4);
    }

    private void adicionarLinhaResumo(Sheet sheet, int rowNum, String label, BigDecimal valor, CellStyle style) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        Cell cell = row.createCell(1);
        cell.setCellValue(valor.doubleValue());
        if (style != null) cell.setCellStyle(style);
    }

    private void criarLinhaCabecalho(Sheet sheet, String[] headers, CellStyle style) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private CellStyle criarEstiloHeader(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle criarEstiloMoeda(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle criarEstiloSubtotal(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private void autoSizeColunas(Sheet sheet, int numColunas) {
        for (int i = 0; i < numColunas; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String nvl(String valor) {
        return valor != null ? valor : "";
    }

    private String formatarDecimal(BigDecimal valor) {
        return valor.toPlainString().replace(".", ",");
    }
}
