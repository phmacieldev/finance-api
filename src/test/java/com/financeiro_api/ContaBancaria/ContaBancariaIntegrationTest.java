package com.financeiro_api.ContaBancaria;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContaBancariaIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "contas.test@teste.com";
    static final String CNPJ  = "17983686000117";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/contas-bancarias";

    String ceoToken;

    @BeforeEach
    void setup() throws Exception {
        setupMvc();
        limparUsuarioEEmpresa(EMAIL, CNPJ);
        ceoToken = provisionarCeo(EMAIL, CNPJ, PASS);
    }

    @AfterEach
    void teardown() {
        limparUsuarioEEmpresa(EMAIL, CNPJ);
    }

    @Test
    void listar_semToken_retorna401ou403() throws Exception {
        mvc.perform(get(BASE))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listar_comToken_retorna200() throws Exception {
        mvc.perform(get(BASE).header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk());
    }

    @Test
    void criar_contaCorrente_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Conta Principal",
                                "banco", "Nubank",
                                "tipo", "CORRENTE"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Conta Principal"))
                .andExpect(jsonPath("$.tipo").value("CORRENTE"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void criar_contaInvestimento_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Reserva",
                                "banco", "XP",
                                "tipo", "INVESTIMENTO"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("INVESTIMENTO"));
    }

    @Test
    void desativar_contaExistente_retorna204() throws Exception {
        var result = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nome", "Para Desativar",
                                "banco", "Bradesco",
                                "tipo", "POUPANCA"
                        ))))
                .andReturn();
        var body = MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
        var id = body.get("id");

        mvc.perform(delete(BASE + "/" + id)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void listar_somenteContasAtivas() throws Exception {
        mvc.perform(post(BASE)
                .header("Authorization", "Bearer " + ceoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("nome", "Ativa", "banco", "Inter", "tipo", "CORRENTE"))));

        var result = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nome", "Desativada", "banco", "Caixa", "tipo", "POUPANCA"))))
                .andReturn();
        var id = MAPPER.readValue(result.getResponse().getContentAsString(), Map.class).get("id");
        mvc.perform(delete(BASE + "/" + id).header("Authorization", "Bearer " + ceoToken));

        mvc.perform(get(BASE).header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nome == 'Desativada')]").isEmpty());
    }
}
