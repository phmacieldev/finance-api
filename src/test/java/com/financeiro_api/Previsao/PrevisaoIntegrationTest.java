package com.financeiro_api.Previsao;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PrevisaoIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "previsoes.test@teste.com";
    static final String CNPJ  = "22222222000191";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/previsoes";

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
    void criar_receita_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "descricao", "Aluguel recebido",
                                "tipo", "RECEITA",
                                "valor", "3500.00",
                                "frequencia", "MENSAL",
                                "dataInicio", "2025-01-01",
                                "diaRecorrencia", 5
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.descricao").value("Aluguel recebido"))
                .andExpect(jsonPath("$.tipo").value("RECEITA"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void criar_despesa_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "descricao", "Conta de luz",
                                "tipo", "DESPESA",
                                "valor", "450.00",
                                "frequencia", "MENSAL",
                                "dataInicio", "2025-01-01",
                                "diaRecorrencia", 10
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DESPESA"));
    }

    @Test
    void listar_retornaPrevisoesCriadas() throws Exception {
        mvc.perform(post(BASE)
                .header("Authorization", "Bearer " + ceoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "descricao", "Salário",
                        "tipo", "RECEITA",
                        "valor", "5000.00",
                        "frequencia", "MENSAL",
                        "dataInicio", "2025-01-01",
                        "diaRecorrencia", 1
                ))));

        mvc.perform(get(BASE).header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void desativar_retorna204ERemoveListagem() throws Exception {
        var criar = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "descricao", "Para desativar",
                                "tipo", "DESPESA",
                                "valor", "200.00",
                                "frequencia", "MENSAL",
                                "dataInicio", "2025-01-01",
                                "diaRecorrencia", 15
                        ))))
                .andReturn();
        var criado = MAPPER.readValue(criar.getResponse().getContentAsString(), Map.class);
        String id = (String) criado.get("id");

        mvc.perform(delete(BASE + "/" + id)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNoContent());

        // após desativar, listagem deve estar vazia
        mvc.perform(get(BASE).header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void desativar_previsaoInexistente_retorna404() throws Exception {
        mvc.perform(delete(BASE + "/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNotFound());
    }
}
