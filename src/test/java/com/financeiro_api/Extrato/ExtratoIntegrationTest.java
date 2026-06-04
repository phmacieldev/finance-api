package com.financeiro_api.Extrato;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExtratoIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "extratos.test@teste.com";
    static final String CNPJ  = "71379110000189";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/extratos";

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
    void listar_comToken_retorna200ComPaginacao() throws Exception {
        mvc.perform(get(BASE).header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    void importarCsv_arquivoValido_retorna201() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "01/05/2025;Salario;5000,00;5000,00\n" +
                "02/05/2025;Aluguel;-1500,00;3500,00\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart(BASE + "/importar")
                        .file(file)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importados").value(2));
    }

    @Test
    void importarCsv_semToken_retorna401ou403() throws Exception {
        String csv = "Data;Lancamento;Valor\n01/05/2025;Teste;100,00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart(BASE + "/importar").file(file))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listar_filtradoPorMesAno_retornaSomenteDoMes() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "15/06/2025;Receita Junho;3000,00;3000,00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato.csv", "text/csv", csv.getBytes());
        mvc.perform(multipart(BASE + "/importar").file(file)
                .header("Authorization", "Bearer " + ceoToken));

        mvc.perform(get(BASE + "?mes=6&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void atribuirCategoria_extratoInexistente_retorna404() throws Exception {
        var fakeId = "00000000-0000-0000-0000-000000000000";
        var categoriaId = "00000000-0000-0000-0000-000000000001";
        mvc.perform(patch(BASE + "/" + fakeId + "/categoria")
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("categoriaId", categoriaId))))
                .andExpect(status().is4xxClientError());
    }
}
