package com.financeiro_api.Categorias;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoriaIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "categorias.test@teste.com";
    static final String CNPJ  = "33382553000199";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/categorias";

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
    void criar_categoriaReceita_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Salários", "tipo", "RECEITA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Salários"))
                .andExpect(jsonPath("$.tipo").value("RECEITA"));
    }

    @Test
    void criar_categoriaDespesa_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Aluguel", "tipo", "DESPESA"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DESPESA"));
    }

    @Test
    void criar_nomeDuplicado_retorna409() throws Exception {
        mvc.perform(post(BASE)
                .header("Authorization", "Bearer " + ceoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Duplicada", "tipo", "DESPESA"))));

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Duplicada", "tipo", "DESPESA"))))
                .andExpect(status().isConflict());
    }

    @Test
    void listarPorTipo_filtraCorretamente() throws Exception {
        mvc.perform(post(BASE)
                .header("Authorization", "Bearer " + ceoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Receita X", "tipo", "RECEITA"))));

        mvc.perform(get(BASE + "?tipo=RECEITA")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tipo == 'DESPESA')]").isEmpty());
    }

    @Test
    void deletar_categoriaExistente_retorna204() throws Exception {
        var result = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Para Deletar", "tipo", "DESPESA"))))
                .andReturn();
        var body = MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
        var id = body.get("id");

        mvc.perform(delete(BASE + "/" + id)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNoContent());
    }
}
