package com.financeiro_api.Dre;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DreIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "dre.test@teste.com";
    static final String CNPJ  = "44444444000191";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/dre";

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
    void dre_semToken_retorna401ou403() throws Exception {
        mvc.perform(get(BASE + "?mes=6&ano=2025"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void dre_semDados_retornaEstrutura() throws Exception {
        mvc.perform(get(BASE + "?mes=6&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value(6))
                .andExpect(jsonPath("$.ano").value(2025))
                .andExpect(jsonPath("$.linhas").isArray())
                .andExpect(jsonPath("$.lucroLiquido").value(0));
    }

    @Test
    void dre_comCategoriasELancamentos_calculaValoresCorretos() throws Exception {
        // 1. criar categoria de receita bruta
        var catResult = mvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Vendas",
                                "tipo", "RECEITA",
                                "dreCategoria", "RECEITA_BRUTA"
                        ))))
                .andReturn();
        var catReceita = MAPPER.readValue(catResult.getResponse().getContentAsString(), Map.class);
        String catReceitaId = (String) catReceita.get("id");

        // 2. criar categoria de despesa administrativa
        var catDespResult = mvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Administrativo",
                                "tipo", "DESPESA",
                                "dreCategoria", "DESPESA_ADMINISTRATIVA"
                        ))))
                .andReturn();
        var catDesp = MAPPER.readValue(catDespResult.getResponse().getContentAsString(), Map.class);
        String catDespId = (String) catDesp.get("id");

        // 3. importar extratos em novembro
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "01/11/2025;Venda produto;8000,00;8000,00\n" +
                "15/11/2025;Despesa admin;-2000,00;6000,00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "dre.csv", "text/csv", csv.getBytes());
        mvc.perform(multipart("/api/v1/extratos/importar")
                .file(file)
                .header("Authorization", "Bearer " + ceoToken));

        // 4. buscar os IDs dos extratos importados
        var listResult = mvc.perform(get("/api/v1/extratos?mes=11&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andReturn();
        var page = MAPPER.readValue(listResult.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> extratos = (List<Map<String, Object>>) page.get("content");

        // 5. atribuir categorias (receita ao positivo, despesa ao negativo)
        for (Map<String, Object> extrato : extratos) {
            String extratoId = (String) extrato.get("id");
            double valor = ((Number) extrato.get("valor")).doubleValue();
            String categoriaId = valor > 0 ? catReceitaId : catDespId;

            mvc.perform(patch("/api/v1/extratos/" + extratoId + "/categoria")
                    .header("Authorization", "Bearer " + ceoToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("categoriaId", categoriaId))));
        }

        // 6. verificar DRE
        mvc.perform(get(BASE + "?mes=11&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lucroLiquido").value(6000.0));
    }
}
