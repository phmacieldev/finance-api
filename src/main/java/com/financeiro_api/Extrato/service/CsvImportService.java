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

            int headerIdx = helper.encontrarHeaderRow(linhas, separador);

            Map<String, Integer> colunas = headerIdx >= 0
                    ? mapearColunas(linhas[headerIdx], separador)
                    : inferirColunasPositional(linhas, 0, separador);

            int startIdx = headerIdx >= 0 ? headerIdx + 1 : 0;

            for (int i = startIdx; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isBlank()) continue;

                if (helper.isHeaderRow(linha, separador)) {
                    colunas = mapearColunas(linha, separador);
                    continue;
                }

                try {
                    String[] campos = dividirLinha(linha, separador);

                    String dataStr = obterCampo(campos, colunas, "data");
                    LocalDate data;
                    try {
                        data = helper.parseData(dataStr);
                    } catch (Exception e) {
                        continue;
                    }

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

    private String detectarEncoding(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);

        if (temMojibake(utf8)) {
            try {
                return new String(utf8.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            } catch (Exception ignored) {}
        }

        long replacements = utf8.chars().filter(c -> c == '?').count();
        if (replacements > 2) {
            return new String(bytes, Charset.forName("ISO-8859-1"));
        }

        return utf8;
    }

    private boolean temMojibake(String s) {
        String[] padroes = { "Ã©", "Ã£", "Ãª", "Ã§", "Ã³", "Ã¡", "Ã ", "Ãµ", "Ã­", "Ã¢", "Ã´", "Ãº" };
        int ocorrencias = 0;
        for (String p : padroes) {
            int idx = 0;
            while ((idx = s.indexOf(p, idx)) != -1) {
                ocorrencias++;
                if (ocorrencias >= 2) return true;
                idx += p.length();
            }
        }
        return false;
    }

    /**
     * Detecta o separador escaneando as primeiras linhas não-vazias do arquivo.
     * Suporta: ponto-e-vírgula (;), vírgula (,), tab (\t), pipe (|).
     */
    private char detectarSeparador(String conteudo) {
        String[] linhas = conteudo.split("\\r?\\n");
        Map<Character, Long> contagens = new HashMap<>();
        int amostras = 0;

        for (String linha : linhas) {
            if (linha.isBlank()) continue;
            contagens.merge(';',  (long) linha.chars().filter(c -> c == ';').count(),  Long::sum);
            contagens.merge(',',  (long) linha.chars().filter(c -> c == ',').count(),  Long::sum);
            contagens.merge('\t', (long) linha.chars().filter(c -> c == '\t').count(), Long::sum);
            contagens.merge('|',  (long) linha.chars().filter(c -> c == '|').count(),  Long::sum);
            if (++amostras >= 5) break;
        }

        return contagens.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .orElse(';');
    }

    // ─── Mapeamento de colunas ───────────────────────────────────────────────────

    /**
     * Mapeia nomes de colunas para índices semânticos.
     * Cobre os formatos dos principais bancos brasileiros:
     * Bradesco, Itaú, Santander, Inter, Nubank, C6, BTG, Caixa, Sicoob, XP, etc.
     */
    private Map<String, Integer> mapearColunas(String headerLine, char separador) {
        String[] headers = dividirLinha(headerLine, separador);
        Map<String, Integer> mapa = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            String h = helper.normalizarHeader(headers[i]);
            if (h.isBlank()) continue;

            // DATA — aceita qualquer variante de "data" ou "date"
            if (!mapa.containsKey("data") && helper.isDataHeader(h)) {
                mapa.put("data", i);

            // CRÉDITO — coluna separada de entrada
            } else if (!mapa.containsKey("credito") && (
                    h.contains("credito") || h.contains("entrada") || h.contains("credit") ||
                    (h.contains("cred") && !h.contains("desc") && !h.contains("incr")))) {
                mapa.put("credito", i);

            // DÉBITO — coluna separada de saída
            } else if (!mapa.containsKey("debito") && (
                    h.contains("debito") || h.contains("saida") || h.contains("debit") ||
                    (h.equals("deb") || h.startsWith("deb_") || h.endsWith("_deb")))) {
                mapa.put("debito", i);

            // VALOR — coluna única de valor (positivo/negativo)
            } else if (!mapa.containsKey("valor") && (
                    h.startsWith("valor") || h.startsWith("vlr") || h.equals("amount") ||
                    h.contains("montante") || h.contains("importe") ||
                    h.equals("valor_lancamento") || h.equals("vlr_lanc"))) {
                mapa.put("valor", i);

            // DESCRIÇÃO / LANÇAMENTO
            } else if (!mapa.containsKey("tipo_pagamento") && (
                    h.startsWith("lancamento") || h.equals("historico") ||
                    h.startsWith("descricao") || h.equals("descr") ||
                    h.startsWith("tipo_pag") || h.startsWith("forma") ||
                    h.equals("tipo") || h.equals("memo") || h.equals("obs") ||
                    h.startsWith("complemento") || h.startsWith("operacao") ||
                    h.equals("titulo") || h.equals("title") ||
                    h.startsWith("informacoes") || h.startsWith("detalhe") ||
                    h.startsWith("estabelecimento") || h.equals("historico_extendido"))) {
                mapa.put("tipo_pagamento", i);

            // RAZÃO SOCIAL / BENEFICIÁRIO
            } else if (!mapa.containsKey("razao_social") && (
                    h.contains("razao") || h.equals("nome") ||
                    h.startsWith("beneficiario") || h.startsWith("favorecido") ||
                    h.startsWith("empresa") || h.equals("comercio") ||
                    h.equals("estabelecimento") || h.startsWith("contrap"))) {
                mapa.put("razao_social", i);

            // CPF / CNPJ
            } else if (!mapa.containsKey("cpf_cnpj") && (
                    h.contains("cpf") || h.contains("cnpj") ||
                    h.startsWith("documento") || h.equals("doc") ||
                    h.equals("nf") || h.equals("nota_fiscal"))) {
                mapa.put("cpf_cnpj", i);

            // SALDO
            } else if (!mapa.containsKey("saldo") && h.startsWith("saldo")) {
                mapa.put("saldo", i);
            }
        }

        return mapa;
    }

    /**
     * Fallback quando não há cabeçalho.
     * Primeiro tenta detectar colunas por conteúdo (data, número, texto).
     * Se não conseguir, usa mapeamento posicional fixo.
     */
    private Map<String, Integer> inferirColunasPositional(String[] linhas, int startIdx, char sep) {
        Map<String, Integer> porConteudo = detectarColunasPorConteudo(linhas, startIdx, sep);
        if (!porConteudo.isEmpty()) return porConteudo;

        Map<String, Integer> mapa = new HashMap<>();
        int numCols = 4;
        for (int i = startIdx; i < Math.min(linhas.length, startIdx + 10); i++) {
            String linha = linhas[i].trim();
            if (!linha.isBlank()) {
                numCols = dividirLinha(linha, sep).length;
                break;
            }
        }

        if (numCols <= 5) {
            mapa.put("data", 0);
            mapa.put("valor", 1);
            mapa.put("tipo_pagamento", 3);
        } else {
            mapa.put("data", 0);
            mapa.put("tipo_pagamento", 1);
            mapa.put("razao_social", 3);
            mapa.put("cpf_cnpj", 4);
            mapa.put("valor", 5);
            mapa.put("saldo", 6);
        }
        return mapa;
    }

    /**
     * Detecta colunas analisando o conteúdo das primeiras linhas de dados.
     * Identifica: coluna de data (parseia como data), coluna de valor (número),
     * coluna de descrição (texto longo).
     */
    private Map<String, Integer> detectarColunasPorConteudo(String[] linhas, int startIdx, char sep) {
        List<String[]> amostras = new ArrayList<>();
        for (int i = startIdx; i < Math.min(linhas.length, startIdx + 15); i++) {
            String linha = linhas[i].trim();
            if (!linha.isBlank()) {
                amostras.add(dividirLinha(linha, sep));
                if (amostras.size() >= 5) break;
            }
        }
        if (amostras.size() < 2) return Collections.emptyMap();

        int numCols = amostras.stream().mapToInt(a -> a.length).max().orElse(0);
        if (numCols < 2) return Collections.emptyMap();

        int[] dateHits   = new int[numCols];
        int[] numHits    = new int[numCols];
        int[] textHits   = new int[numCols];

        for (String[] amostra : amostras) {
            for (int col = 0; col < numCols && col < amostra.length; col++) {
                String val = amostra[col].trim().replace("\"", "");
                if (val.isBlank()) continue;

                try { helper.parseData(val); dateHits[col]++; } catch (Exception ignored) {}

                BigDecimal num = helper.parsarDecimalBrasileiro(val);
                if (num != null && num.abs().compareTo(BigDecimal.ZERO) > 0) numHits[col]++;

                if (val.length() > 4 && num == null) textHits[col]++;
            }
        }

        Map<String, Integer> mapa = new HashMap<>();
        int threshold = Math.max(1, amostras.size() / 2);

        // Coluna de data: maioria dos valores parseia como data
        for (int col = 0; col < numCols; col++) {
            if (dateHits[col] >= threshold && !mapa.containsKey("data")) {
                mapa.put("data", col);
                break;
            }
        }

        // Coluna de valor: maioria são números, e não é a coluna de data
        for (int col = numCols - 1; col >= 0; col--) {
            if (numHits[col] >= threshold && !Objects.equals(mapa.get("data"), col)
                    && !mapa.containsKey("valor")) {
                mapa.put("valor", col);
                break;
            }
        }

        // Coluna de saldo: segundo número mais frequente (coluna imediatamente após valor)
        Integer valorIdx = mapa.get("valor");
        if (valorIdx != null && valorIdx + 1 < numCols && numHits[valorIdx + 1] >= threshold) {
            mapa.put("saldo", valorIdx + 1);
        }

        // Coluna de descrição: texto longo, não é data nem número
        for (int col = 0; col < numCols; col++) {
            if (textHits[col] >= threshold
                    && !Objects.equals(mapa.get("data"), col)
                    && !Objects.equals(mapa.get("valor"), col)
                    && !Objects.equals(mapa.get("saldo"), col)
                    && !mapa.containsKey("tipo_pagamento")) {
                mapa.put("tipo_pagamento", col);
                break;
            }
        }

        return mapa;
    }

    // ─── Cálculo de valor ────────────────────────────────────────────────────────

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
