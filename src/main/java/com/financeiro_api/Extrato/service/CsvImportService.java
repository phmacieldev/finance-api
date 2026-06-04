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
            if (headerIdx < 0) {
                throw new IllegalArgumentException(
                        "Cabeçalho não encontrado. Verifique se o arquivo possui as colunas: Data, Valor.");
            }

            Map<String, Integer> colunas = mapearColunas(linhas[headerIdx], separador);

            for (int i = headerIdx + 1; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isBlank()) continue;

                try {
                    String[] campos = dividirLinha(linha, separador);

                    // Pula linhas de saldo/resumo que não são transações
                    String lancamento = obterCampo(campos, colunas, "tipo_pagamento");
                    if (helper.deveIgnorar(lancamento)) continue;

                    // Pula linhas sem valor de transação
                    String valorStr = obterCampo(campos, colunas, "valor");
                    if (valorStr == null || valorStr.isBlank()) continue;

                    BigDecimal valor = helper.parsarDecimalBrasileiro(valorStr);
                    if (valor == null) continue;

                    LocalDate data = helper.parseData(obterCampo(campos, colunas, "data"));
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

    // Detecta encoding: tenta UTF-8, fallback para ISO-8859-1 (Windows-1252)
    private String detectarEncoding(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        long replacements = utf8.chars().filter(c -> c == '�').count();
        if (replacements > 2) {
            return new String(bytes, Charset.forName("ISO-8859-1"));
        }
        return utf8;
    }

    private char detectarSeparador(String conteudo) {
        String primeiraLinha = conteudo.split("\\r?\\n")[0];
        long pontoVirgulas = primeiraLinha.chars().filter(c -> c == ';').count();
        long virgulas = primeiraLinha.chars().filter(c -> c == ',').count();
        return (pontoVirgulas >= virgulas) ? ';' : ',';
    }

    private Map<String, Integer> mapearColunas(String headerLine, char separador) {
        String[] headers = dividirLinha(headerLine, separador);
        Map<String, Integer> mapa = new HashMap<>();

        for (int i = 0; i < headers.length; i++) {
            String h = helper.normalizarHeader(headers[i]);

            // Data: campo começando com "data"
            if (!mapa.containsKey("data") && (h.equals("data") || h.startsWith("data_"))) {
                mapa.put("data", i);
            }
            // Tipo/Lançamento: lancamento, historico, descricao, tipo, forma
            else if (!mapa.containsKey("tipo_pagamento") && (
                    h.startsWith("lancamento") || h.equals("historico") ||
                    h.startsWith("descricao") || h.equals("tipo") ||
                    h.startsWith("tipo_pag") || h.startsWith("forma"))) {
                mapa.put("tipo_pagamento", i);
            }
            // Razão Social: contém "razao" ou é "nome", "beneficiario", "favorecido"
            else if (!mapa.containsKey("razao_social") && (
                    h.contains("razao") || h.equals("nome") ||
                    h.startsWith("beneficiario") || h.startsWith("favorecido"))) {
                mapa.put("razao_social", i);
            }
            // CPF/CNPJ: contém "cpf" ou "cnpj"
            else if (!mapa.containsKey("cpf_cnpj") && (h.contains("cpf") || h.contains("cnpj"))) {
                mapa.put("cpf_cnpj", i);
            }
            // Valor: começa com "valor" (cobre "valor", "valor_r", "valor_r$", etc.)
            else if (!mapa.containsKey("valor") && h.startsWith("valor")) {
                mapa.put("valor", i);
            }
            // Saldo: começa com "saldo" (cobre "saldo", "saldo_r", "saldo_r$", etc.)
            else if (!mapa.containsKey("saldo") && h.startsWith("saldo")) {
                mapa.put("saldo", i);
            }
            // ag_origem e outros campos são ignorados
        }

        // Fallback posicional se não encontrou data + valor pelo cabeçalho
        if (!mapa.containsKey("data") || !mapa.containsKey("valor")) {
            mapa.clear();
            mapa.put("data", 0);
            mapa.put("tipo_pagamento", 1);
            // posição 2 ignorada (ag/origem)
            mapa.put("razao_social", 3);
            mapa.put("cpf_cnpj", 4);
            mapa.put("valor", 5);
            mapa.put("saldo", 6);
        }

        return mapa;
    }

    private String obterCampo(String[] campos, Map<String, Integer> colunas, String chave) {
        Integer idx = colunas.get(chave);
        if (idx == null || idx >= campos.length) return null;
        return campos[idx].trim().replace("\"", "");
    }

    // Divide linha respeitando aspas
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
