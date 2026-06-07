package com.financeiro_api.Dashboard;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "dashboard.test@teste.com";
    static final String CNPJ  = "33333333000191";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/dashboard";

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
    void dashboard_semToken_retorna401ou403() throws Exception {
        mvc.perform(get(BASE + "?mes=6&ano=2025"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void dashboard_semDados_retornaZeros() throws Exception {
        mvc.perform(get(BASE + "?mes=6&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mes").value(6))
                .andExpect(jsonPath("$.ano").value(2025))
                .andExpect(jsonPath("$.totalEntradas").value(0))
                .andExpect(jsonPath("$.totalSaidas").value(0))
                .andExpect(jsonPath("$.fluxoDiario").isArray());
    }

    @Test
    void dashboard_comDadosImportados_calculaTotaisCorretos() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "05/09/2025;Receita A;4000,00;4000,00\n" +
                "10/09/2025;Receita B;1000,00;5000,00\n" +
                "15/09/2025;Despesa X;-2000,00;3000,00\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "dashboard.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/v1/extratos/importar")
                .file(file)
                .header("Authorization", "Bearer " + ceoToken));

        mvc.perform(get(BASE + "?mes=9&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntradas").value(5000.0))
                .andExpect(jsonPath("$.totalSaidas").value(2000.0))
                .andExpect(jsonPath("$.transacoesSemCategoria").value(3))
                .andExpect(jsonPath("$.ultimosLancamentos").isArray());
    }

    @Test
    void dashboard_ultimosLancamentos_ordenadosPorDataDesc() throws Exception {
        // cria 3 lançamentos manuais em datas diferentes
        String[] datas = { "2025-10-01", "2025-10-15", "2025-10-08" };
        for (String data : datas) {
            mvc.perform(post("/api/v1/extratos")
                    .header("Authorization", "Bearer " + ceoToken)
                    .contentType(APPLICATION_JSON)
                    .content(json(Map.of(
                            "data", data,
                            "descricao", "Lancamento " + data,
                            "valor", "100.00",
                            "tipo", "RECEITA"
                    ))));
        }

        mvc.perform(get(BASE + "?mes=10&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                // o primeiro lançamento na lista deve ser o mais recente (dia 15)
                .andExpect(jsonPath("$.ultimosLancamentos[0].data").value("2025-10-15"));
    }
}
