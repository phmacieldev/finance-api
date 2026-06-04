package com.financeiro_api.Extrato.service;

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

    public ExtratoImportHelper(ExtratoRepository repository) {
        this.repository = repository;
    }

    public boolean deveIgnorar(String lancamento) {
        if (lancamento == null || lancamento.isBlank()) return true;
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
        // Gera um ID único para este lote — permite desfazer a importação inteira
        UUID batchId = extratos.isEmpty() ? null : UUID.randomUUID();
        int importados = 0, duplicatas = 0, erros = 0;

        for (Extrato e : extratos) {
            try {
                if (repository.existsByEnterpriseIdAndImportHash(tenantId, e.getImportHash())) {
                    duplicatas++;
                } else {
                    e.setImportBatchId(batchId);
                    repository.save(e);
                    importados++;
                }
            } catch (Exception ex) {
                erros++;
            }
        }

        String batchIdStr = (importados > 0 && batchId != null) ? batchId.toString() : null;
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
                .replaceAll("[^a-z0-9]", "_")   // tudo que não é letra/número vira _
                .replaceAll("_+", "_")           // colapsa múltiplos _
                .replaceAll("^_|_$", "");        // remove _ inicial/final
    }

    // Detecta a linha do cabeçalho real (ignora metadados do banco)
    public int encontrarHeaderRow(String[] linhas, char separador) {
        for (int i = 0; i < Math.min(linhas.length, 20); i++) {
            String linha = normalizarHeader(linhas[i]);
            String[] campos = linhas[i].split(String.valueOf(separador), -1);
            // Linha de cabeçalho tem "data" no primeiro campo e "valor" em algum campo
            if (campos.length >= 3) {
                String primeiroNormalizado = normalizarHeader(campos[0]);
                boolean temData = primeiroNormalizado.equals("data") || primeiroNormalizado.startsWith("data_");
                boolean temValor = linha.contains("valor");
                if (temData && temValor) return i;
            }
        }
        return -1;
    }

    public LocalDate parseData(String str) {
        if (str == null || str.isBlank()) throw new IllegalArgumentException("Data em branco");
        String limpo = str.trim().replace("\"", "");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(limpo, fmt); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Formato de data não reconhecido: " + str);
    }

    // Suporta formato brasileiro (1.234,56) e padrão (1234.56)
    public BigDecimal parsarDecimalBrasileiro(String str) {
        if (str == null || str.isBlank()) return null;
        String limpo = str.trim().replace("\"", "").replace(" ", "").replace(" ", "");
        if (limpo.isEmpty()) return null;
        if (limpo.contains(",")) {
            // 1.234,56 → 1234.56
            limpo = limpo.replace(".", "").replace(",", ".");
        }
        try {
            return new BigDecimal(limpo);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String gerarHash(LocalDate data, String tipo, String razaoSocial, BigDecimal valor, UUID contaBancariaId) {
        String base = String.join("|",
                data != null ? data.toString() : "",
                tipo != null ? tipo.toUpperCase().trim() : "",
                razaoSocial != null ? razaoSocial.toUpperCase().trim() : "",
                valor != null ? valor.toPlainString() : ""
        );
        // Include bank account in hash only when specified, to keep backward compatibility
        String entrada = contaBancariaId != null ? base + "|" + contaBancariaId : base;
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
