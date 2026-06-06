package com.financeiro_api.Extrato.service;

import com.financeiro_api.Extrato.domain.Extrato;
import com.financeiro_api.Extrato.dto.ExtratoImportResultDTO;
import com.financeiro_api.shared.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class CsvImportService {

    private final ExtratoImportHelper helper;

    public CsvImportService(ExtratoImportHelper helper) {
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

        try {
            byte[] bytes = file.getBytes();
            String conteudo = detectarEncoding(bytes);
            conteudo = removerBom(conteudo);

            char separador = detectarSeparador(conteudo);
            String[] linhas = conteudo.split("\\r?\\n");

            // Localiza o cabeçalho real (ignora metadados do banco)
            int headerIdx = helper.encontrarHeaderRow(linhas, separador);

            // Define mapeamento de colunas — com cabeçalho ou fallback posicional
            Map<String, Integer> colunas = headerIdx >= 0
                    ? mapearColunas(linhas[headerIdx], separador)
                    : inferirColunasPositional(linhas, 0, separador);

            int startIdx = headerIdx >= 0 ? headerIdx + 1 : 0;

            for (int i = startIdx; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isBlank()) continue;

                // Detecta nova seção com cabeçalho (ex: "Últimos Lançamentos" no Bradesco)
                if (helper.isHeaderRow(linha, separador)) {
                    colunas = mapearColunas(linha, separador);
                    continue;
                }

                try {
                    String[] campos = dividirLinha(linha, separador);

                    // Data inválida → linha de metadado/rodapé → ignorar silenciosamente
                    String dataStr = obterCampo(campos, colunas, "data");
                    LocalDate data;
                    try {
                        data = helper.parseData(dataStr);
                    } catch (Exception e) {
                        continue;
                    }

                    // Calcula valor: coluna única OU crédito/débito separados
                    BigDecimal valor = calcularValor(campos, colunas);
                    if (valor == null || valor.compareTo(BigDecimal.ZERO) == 0) continue;

                    String lancamento = obterCampo(campos, colunas, "tipo_pagamento");
                    if (helper.deveIgnorar(lancamento)) continue;

                    String razaoSocial = obterCampo(campos, colunas, "razao_social");
                    String cpfCnpj = obterCampo(campos, colunas, "cpf_cnpj");
                    String saldoStr = obterCampo(campos, colunas, "saldo");
                    BigDecimal saldo = helper.parsarDecimalBrasileiro(saldoStr);

                    extratos.add(helper.construirExtrato(tenantId, data, lancamento,
                            razaoSocial, cpfCnpj, valor, saldo, contaBancariaId));

                } catch (Exception e) {
                    erros++;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao processar CSV: " + e.getMessage());
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

    // ─── Encoding ────────────────────────────────────────────────────────────────

    /**
     * Detecta encoding do arquivo:
     * 1. Tenta UTF-8 e verifica mojibake (ex: "TransferÃªncia" — UTF-8 bytes lidos como Latin-1)
     * 2. Verifica arquivo Latin-1 genuíno (bytes inválidos UTF-8 → caractere de substituição)
     * 3. Fallback: UTF-8 puro
     */
    private String detectarEncoding(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);

        // Caso 1: arquivo UTF-8 mas com mojibake embutido (bug de exportação do banco)
        // Ex: Bradesco app exporta "Ã©" no lugar de "é"
        if (temMojibake(utf8)) {
            try {
                return new String(utf8.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        // Caso 2: arquivo genuinamente Latin-1/Windows-1252
        long replacements = utf8.chars().filter(c -> c == '�').count();
        if (replacements > 2) {
            return new String(bytes, Charset.forName("ISO-8859-1"));
        }

        return utf8;
    }

    /**
     * Detecta mojibake típico de português:
     * Ã© = é, Ã£ = ã, Ãª = ê, Ã§ = ç, Ã³ = ó, Ã¡ = á, Ã  = à, Ãµ = õ, Ã­ = í
     */
    private boolean temMojibake(String s) {
        return s.contains("Ã©") || s.contains("Ã£") || s.contains("Ãª")
                || s.contains("Ã§") || s.contains("Ã³") || s.contains("Ã¡")
                || s.contains("Ã ") || s.contains("Ãµ") || s.contains("Ã­")
                || s.contains("Ã¢") || s.contains("Ã´") || s.contains("Ãº");
    }

    private char detectarSeparador(String conteudo) {
        String primeiraLinha = conteudo.split("\\r?\\n")[0];
        long pontoVirgulas = primeiraLinha.chars().filter(c -> c == ';').count();
        long virgulas = primeiraLinha.chars().filter(c -> c == ',').count();
        return (pontoVirgulas >= virgulas) ? ';' : ',';
    }

    // ─── Mapeamento de colunas ───────────────────────────────────────────────────

    private Map<String, Integer> mapearColunas(String headerLine, char separador) {
        String[] headers = dividirLinha(headerLine, separador);
        Map<String, Integer> mapa = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            String h = helper.normalizarHeader(headers[i]);

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
            } else if (!mapa.containsKey("credito") && h.contains("credito")) {
                // Ex: "Crédito (R$)" → "credito_r_" → contains("credito")
                mapa.put("credito", i);
            } else if (!mapa.containsKey("debito") && h.contains("debito")) {
                // Ex: "Débito (R$)" → "debito_r_" → contains("debito")
                mapa.put("debito", i);
            } else if (!mapa.containsKey("valor") && h.startsWith("valor")) {
                mapa.put("valor", i);
            } else if (!mapa.containsKey("saldo") && h.startsWith("saldo")) {
                mapa.put("saldo", i);
            }
        }

        return mapa;
    }

    /**
     * Fallback posicional quando o arquivo não tem cabeçalho.
     * Detecta automaticamente o número de colunas para cobrir diferentes formatos.
     *
     * Formato curto (≤5 cols) — ex: Bradesco app:
     *   col 0: Data | col 1: Valor | col 2: Doc/UUID (ignorado) | col 3: Descrição
     *
     * Formato longo (≥6 cols) — ex: exportações com 7 colunas fixas:
     *   col 0: Data | col 1: Tipo | col 2: Ag (ignorado) | col 3: Razão Social
     *   col 4: CPF/CNPJ | col 5: Valor | col 6: Saldo
     */
    private Map<String, Integer> inferirColunasPositional(String[] linhas, int startIdx, char sep) {
        Map<String, Integer> mapa = new HashMap<>();

        int numCols = 4; // padrão conservador
        for (int i = startIdx; i < Math.min(linhas.length, startIdx + 10); i++) {
            String linha = linhas[i].trim();
            if (!linha.isBlank()) {
                numCols = dividirLinha(linha, sep).length;
                break;
            }
        }

        if (numCols <= 5) {
            // Formato curto: Data, Valor, Doc/UUID, Descrição
            mapa.put("data", 0);
            mapa.put("valor", 1);
            mapa.put("tipo_pagamento", 3);
        } else {
            // Formato longo: Data, Tipo, Ag, Razão Social, CPF/CNPJ, Valor, Saldo
            mapa.put("data", 0);
            mapa.put("tipo_pagamento", 1);
            mapa.put("razao_social", 3);
            mapa.put("cpf_cnpj", 4);
            mapa.put("valor", 5);
            mapa.put("saldo", 6);
        }

        return mapa;
    }

    // ─── Cálculo de valor ────────────────────────────────────────────────────────

    /**
     * Calcula o valor da transação.
     * Se o arquivo tem colunas separadas de Crédito/Débito (ex: Bradesco internet banking),
     * crédito vira positivo e débito vira negativo.
     * Caso contrário usa coluna "valor" diretamente.
     */
    private BigDecimal calcularValor(String[] campos, Map<String, Integer> colunas) {
        if (colunas.containsKey("credito") || colunas.containsKey("debito")) {
            BigDecimal credito = helper.parsarDecimalBrasileiro(obterCampo(campos, colunas, "credito"));
            BigDecimal debito  = helper.parsarDecimalBrasileiro(obterCampo(campos, colunas, "debito"));

            if (credito != null && credito.compareTo(BigDecimal.ZERO) != 0) return credito;
            if (debito  != null && debito.compareTo(BigDecimal.ZERO)  != 0) return debito.negate();
            return null;
        }
        return helper.parsarDecimalBrasileiro(obterCampo(campos, colunas, "valor"));
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────────

    private String obterCampo(String[] campos, Map<String, Integer> colunas, String chave) {
        Integer idx = colunas.get(chave);
        if (idx == null || idx >= campos.length) return null;
        return campos[idx].trim().replace("\"", "");
    }

    /** Divide linha respeitando campos entre aspas (RFC 4180). */
    private String[] dividirLinha(String linha, char sep) {
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean dentroDasAspas = false;

        for (char c : linha.toCharArray()) {
            if (c == '"') {
                dentroDasAspas = !dentroDasAspas;
            } else if (c == sep && !dentroDasAspas) {
                campos.add(campo.toString());
                campo = new StringBuilder();
            } else {
                campo.append(c);
            }
        }
        campos.add(campo.toString());
        return campos.toArray(new String[0]);
    }

    private String removerBom(String conteudo) {
        if (conteudo != null && conteudo.startsWith("﻿")) return conteudo.substring(1);
        return conteudo;
    }
}
