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
    static final String CNPJ  = "33555000000107";
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

    @Test
    void criarManual_retorna201() throws Exception {
        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "data", "2025-06-10",
                                "descricao", "Venda manual",
                                "valor", "1500.00",
                                "tipo", "RECEITA"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(1500.0));
    }

    @Test
    void editar_lancamento_retorna200() throws Exception {
        // criar
        var criar = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "data", "2025-06-10",
                                "descricao", "Despesa original",
                                "valor", "300.00",
                                "tipo", "DESPESA"
                        ))))
                .andReturn();
        var criado = MAPPER.readValue(criar.getResponse().getContentAsString(), Map.class);
        String id = (String) criado.get("id");

        // editar
        mvc.perform(patch(BASE + "/" + id)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("descricao", "Despesa editada"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.razaoSocial").value("Despesa editada"));
    }

    @Test
    void deletar_lancamento_retorna204() throws Exception {
        var criar = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + ceoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "data", "2025-06-11",
                                "descricao", "Para deletar",
                                "valor", "100.00",
                                "tipo", "DESPESA"
                        ))))
                .andReturn();
        var criado = MAPPER.readValue(criar.getResponse().getContentAsString(), Map.class);
        String id = (String) criado.get("id");

        mvc.perform(delete(BASE + "/" + id)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void importarCsv_duplicata_naoImportaNovamente() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "10/05/2025;Duplicata A;2000,00;2000,00\n" +
                "11/05/2025;Duplicata B;-500,00;1500,00\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "dedup.csv", "text/csv", csv.getBytes());

        // primeira importação — 2 importados
        mvc.perform(multipart(BASE + "/importar")
                        .file(file)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importados").value(2))
                .andExpect(jsonPath("$.duplicatasIgnoradas").value(0));

        // segunda importação com o mesmo arquivo — 2 duplicatas
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "dedup.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart(BASE + "/importar")
                        .file(file2)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importados").value(0))
                .andExpect(jsonPath("$.duplicatasIgnoradas").value(2));
    }

    @Test
    void semCategoria_retornaLancamentosSemCategoria() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "01/07/2025;Receita sem cat;1000,00;1000,00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "semcat.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart(BASE + "/importar")
                .file(file)
                .header("Authorization", "Bearer " + ceoToken));

        mvc.perform(get(BASE + "/sem-categoria?mes=7&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cancelarLote_retorna204ERemoveItens() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "05/08/2025;Item lote;800,00;800,00\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "lote.csv", "text/csv", csv.getBytes());

        var importar = mvc.perform(multipart(BASE + "/importar")
                        .file(file)
                        .header("Authorization", "Bearer " + ceoToken))
                .andReturn();
        var resultado = MAPPER.readValue(importar.getResponse().getContentAsString(), Map.class);
        String batchId = (String) resultado.get("batchId");

        mvc.perform(delete(BASE + "/batch/" + batchId)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isNoContent());

        // após cancelar, extrato do mês deve estar vazio
        mvc.perform(get(BASE + "?mes=8&ano=2025")
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
