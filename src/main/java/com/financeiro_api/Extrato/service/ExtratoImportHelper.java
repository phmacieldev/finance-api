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
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    // Linhas de saldo/resumo que NÃO são transações reais
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

    // Normaliza cabeçalho removendo acentos e caracteres especiais
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
     * Aceita colunas "valor", "credito" ou "debito" como indicador de cabeçalho.
     * Retorna -1 se nenhum cabeçalho encontrado (usar fallback posicional).
     */
    public int encontrarHeaderRow(String[] linhas, char separador) {
        for (int i = 0; i < Math.min(linhas.length, 20); i++) {
            String[] campos = linhas[i].split(String.valueOf(separador), -1);
            if (campos.length >= 2) {
                String primeiro = normalizarHeader(campos[0]);
                boolean temData = primeiro.equals("data") || primeiro.startsWith("data_");
                if (temData) {
                    String linhaCompleta = normalizarHeader(linhas[i]);
                    boolean temValorOuCredito = linhaCompleta.contains("valor")
                            || linhaCompleta.contains("credito")
                            || linhaCompleta.contains("debito");
                    if (temValorOuCredito) return i;
                }
            }
        }
        return -1; // sem cabeçalho — usar fallback posicional
    }

    /**
     * Verifica se uma linha é um cabeçalho de seção (para suporte a múltiplas seções).
     */
    public boolean isHeaderRow(String linha, char separador) {
        String[] campos = linha.split(String.valueOf(separador), -1);
        if (campos.length < 2) return false;
        String primeiro = normalizarHeader(campos[0]);
        boolean temData = primeiro.equals("data") || primeiro.startsWith("data_");
        String linhaCompleta = normalizarHeader(linha);
        boolean temValorOuCredito = linhaCompleta.contains("valor")
                || linhaCompleta.contains("credito")
                || linhaCompleta.contains("debito");
        return temData && temValorOuCredito;
    }

    public LocalDate parseData(String str) {
        if (str == null || str.isBlank()) throw new IllegalArgumentException("Data em branco");
        String limpo = str.trim().replace("\"", "");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(limpo, fmt); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Formato de data não reconhecido: " + str);
    }

    /**
     * Suporta:
     * - Formato brasileiro: 1.234,56
     * - Formato padrão: 1234.56
     * - Prefixo R$: "R$ 263,00", "-R$ 195,00", "R$263,00"
     */
    public BigDecimal parsarDecimalBrasileiro(String str) {
        if (str == null || str.isBlank()) return null;
        // Remove aspas e espaços (inclusive espaço não-quebrável)
        String limpo = str.trim().replace("\"", "").replace(" ", "").replace(" ", "");
        if (limpo.isEmpty()) return null;

        // Preserva sinal negativo antes de remover prefixo R$
        boolean negativo = limpo.startsWith("-");
        // Remove prefixo R$ com ou sem sinal: "-R$263,00" → "263,00", "R$ 263,00" → "263,00"
        limpo = limpo.replaceAll("(?i)-?R\\$\\s*", "").trim();
        if (negativo && !limpo.startsWith("-")) limpo = "-" + limpo;

        if (limpo.isEmpty()) return null;

        if (limpo.contains(",")) {
            // Formato brasileiro: 1.234,56 → 1234.56
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String gerarHash(LocalDate data, String tipo, String razaoSocial, BigDecimal valor, UUID contaBancariaId) {
        // Hash baseado apenas no conteúdo da transação — contaBancariaId é metadado atribuído depois.
        // Isso garante que o mesmo extrato importado com/sem conta não gere duplicatas.
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

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }
}
