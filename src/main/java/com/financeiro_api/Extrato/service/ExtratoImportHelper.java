package com.financeiro_api.Extrato.service;

import com.financeiro_api.Audit.domain.AuditAction;
import com.financeiro_api.Audit.service.AuditLogService;
import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.dto.ExtratoImportResultDTO;
import com.financeiro_api.Extrato.repository.ExtratoRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class ExtratoImportHelper {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d/M/yy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd.MM.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("MM-dd-yyyy")
    );

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "SALDO TOTAL",
            "SALDO MOVIMENTA",
            "SALDO APLIC",
            "SALDO ANTERIOR",
            "SALDO DISPONIVEL",
            "SALDO DISPON"
    );

    private final ExtratoRepository repository;
    private final AuditLogService auditLogService;

    public ExtratoImportHelper(ExtratoRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    public boolean deveIgnorar(String lancamento) {
        if (lancamento == null || lancamento.isBlank()) return false;
        String upper = lancamento.toUpperCase().trim();
        return SKIP_PREFIXES.stream().anyMatch(upper::startsWith);
    }

    public Extrato construirExtrato(UUID tenantId, LocalDate data, String tipo,
                                    String razaoSocial, String cpfCnpj,
                                    BigDecimal valor, BigDecimal saldo,
                                    UUID contaBancariaId) {
        String hash = gerarHash(data, tipo, razaoSocial, valor, contaBancariaId);
        return Extrato.builder()
                .enterpriseId(tenantId)
                .data(data)
                .tipoPagamento(normalizar(tipo))
                .razaoSocial(normalizar(razaoSocial))
                .cpfCnpj(normalizar(cpfCnpj))
                .valor(valor)
                .saldo(saldo)
                .contaBancariaId(contaBancariaId)
                .mes(data.getMonthValue())
                .ano(data.getYear())
                .importHash(hash)
                .build();
    }

    public Extrato construirExtrato(UUID tenantId, LocalDate data, String tipo,
                                    String razaoSocial, String cpfCnpj,
                                    BigDecimal valor, BigDecimal saldo) {
        return construirExtrato(tenantId, data, tipo, razaoSocial, cpfCnpj, valor, saldo, null);
    }

    public ExtratoImportResultDTO salvarLote(List<Extrato> extratos, UUID tenantId) {
        if (extratos.isEmpty()) {
            return new ExtratoImportResultDTO(0, 0, 0, null, "Importados: 0 | Duplicatas ignoradas: 0 | Erros: 0");
        }

        UUID batchId = UUID.randomUUID();

        Set<String> hashesExistentes = repository.findHashesByEnterpriseId(tenantId);

        List<Extrato> novos = new java.util.ArrayList<>();
        int duplicatas = 0;
        for (Extrato e : extratos) {
            if (hashesExistentes.contains(e.getImportHash())) {
                duplicatas++;
            } else {
                e.setImportBatchId(batchId);
                novos.add(e);
            }
        }

        int erros = 0;
        int importados = 0;
        if (!novos.isEmpty()) {
            try {
                repository.saveAll(novos);
                importados = novos.size();
            } catch (Exception ex) {
                erros = novos.size();
            }
        }

        String batchIdStr = importados > 0 ? batchId.toString() : null;
        if (importados > 0) {
            auditLogService.log(AuditAction.EXTRATO_IMPORTED, "Extrato", batchIdStr);
        }
        return new ExtratoImportResultDTO(importados, duplicatas, erros, batchIdStr,
                String.format("Importados: %d | Duplicatas ignoradas: %d | Erros: %d",
                        importados, duplicatas, erros));
    }

    public String normalizarHeader(String h) {
        if (h == null) return "";
        return h.trim().toLowerCase()
                .replaceAll("[áàâãä]", "a")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[íìîï]", "i")
                .replaceAll("[óòôõö]", "o")
                .replaceAll("[úùûü]", "u")
                .replaceAll("[ç]", "c")
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /**
     * Detecta a linha do cabeçalho real (ignora metadados do banco).
     * Verifica qualquer coluna da linha — não exige que a primeira seja "data".
     */
    public int encontrarHeaderRow(String[] linhas, char separador) {
        for (int i = 0; i < Math.min(linhas.length, 30); i++) {
            String linha = linhas[i].trim();
            if (linha.isBlank()) continue;
            String[] campos = linha.split(escaparSeparador(separador), -1);
            if (campos.length < 2) continue;

            boolean temDataCol = false;
            boolean temValorCol = false;
            for (String campo : campos) {
                String h = normalizarHeader(campo);
                if (isDataHeader(h)) temDataCol = true;
                if (isValorHeader(h)) temValorCol = true;
            }
            if (temDataCol && temValorCol) return i;
        }
        return -1;
    }

    public boolean isHeaderRow(String linha, char separador) {
        String[] campos = linha.split(escaparSeparador(separador), -1);
        if (campos.length < 2) return false;
        boolean temDataCol = false;
        boolean temValorCol = false;
        for (String campo : campos) {
            String h = normalizarHeader(campo);
            if (isDataHeader(h)) temDataCol = true;
            if (isValorHeader(h)) temValorCol = true;
        }
        return temDataCol && temValorCol;
    }

    public boolean isDataHeader(String h) {
        return h.equals("data") || h.startsWith("data_") || h.startsWith("dt_") ||
               h.equals("date") || h.equals("competencia") || h.equals("movimento") ||
               h.equals("dt") || h.equals("data_do_balancete") ||
               h.contains("data_lanc") || h.contains("data_mov") ||
               h.contains("data_oper") || h.contains("data_trans") ||
               h.contains("dt_lanc") || h.contains("dt_mov") || h.contains("dt_oper");
    }

    public boolean isValorHeader(String h) {
        return h.contains("valor") || h.contains("vlr") ||
               h.contains("credito") || h.contains("debito") ||
               h.contains("amount") || h.contains("montante") ||
               h.contains("importe") || h.contains("entrada") ||
               h.contains("saida") || h.contains("cred") || h.contains("deb");
    }

    public LocalDate parseData(String str) {
        if (str == null || str.isBlank()) throw new IllegalArgumentException("Data em branco");
        String limpo = str.trim().replace("\"", "");

        // Remove parte de hora se vier timestamp (ex: "2025-01-01 00:00:00" ou "2025-01-01T00:00:00")
        if (limpo.length() > 10 && (limpo.charAt(10) == ' ' || limpo.charAt(10) == 'T')) {
            limpo = limpo.substring(0, 10);
        }

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(limpo, fmt); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Formato de data não reconhecido: " + str);
    }

    /**
     * Suporta:
     * - Formato brasileiro: 1.234,56
     * - Formato internacional: 1234.56
     * - Prefixo R$: "R$ 263,00", "-R$ 195,00"
     * - Notação contábil: (1.234,56) → negativo
     * - Sufixo D/C (Itaú): "263,00 D" → débito negativo, "263,00 C" → crédito positivo
     */
    public BigDecimal parsarDecimalBrasileiro(String str) {
        if (str == null || str.isBlank()) return null;
        String limpo = str.trim().replace("\"", "").replace(" ", "").replace(" ", "");
        if (limpo.isEmpty()) return null;

        // Notação contábil: (1.234,56) → negativo
        boolean contabil = limpo.startsWith("(") && limpo.endsWith(")");
        if (contabil) limpo = "-" + limpo.substring(1, limpo.length() - 1);

        // Sufixo D (débito) ou C (crédito) — ex: "263,00D" ou "263,00C"
        boolean sufixoD = limpo.toUpperCase().endsWith("D") && !limpo.toUpperCase().endsWith("RD");
        boolean sufixoC = limpo.toUpperCase().endsWith("C") && !limpo.toUpperCase().endsWith("RC");
        if (sufixoD || sufixoC) limpo = limpo.substring(0, limpo.length() - 1);

        boolean negativo = limpo.startsWith("-");
        limpo = limpo.replaceAll("(?i)-?R\\$\\s*", "").trim();
        if (negativo && !limpo.startsWith("-")) limpo = "-" + limpo;
        if (sufixoD && !limpo.startsWith("-")) limpo = "-" + limpo;

        if (limpo.isEmpty()) return null;

        if (limpo.contains(",")) {
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String gerarHash(LocalDate data, String tipo, String razaoSocial, BigDecimal valor, UUID contaBancariaId) {
        String entrada = String.join("|",
                data != null ? data.toString() : "",
                tipo != null ? tipo.toUpperCase().trim() : "",
                razaoSocial != null ? razaoSocial.toUpperCase().trim() : "",
                valor != null ? valor.toPlainString() : ""
        );
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(entrada.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 64);
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /** Escapa o separador para uso no split (tab e pipe precisam de escape). */
    public String escaparSeparador(char sep) {
        if (sep == '\t') return "\\t";
        if (sep == '|') return "\\|";
        return String.valueOf(sep);
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
