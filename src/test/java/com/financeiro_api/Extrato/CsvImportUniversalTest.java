package com.financeiro_api.Extrato;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa o import universal de CSV cobrindo formatos dos principais bancos brasileiros.
 */
class CsvImportUniversalTest extends TenantIntegrationTestBase {

    static final String EMAIL = "csvuniversal.test@teste.com";
    static final String CNPJ  = "11333888000109";
    static final String PASS  = "senha123";
    static final String BASE  = "/api/v1/extratos/importar";

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
    void formato_padrao_ponto_virgula() throws Exception {
        // Formato C6 / Inter / Santander básico
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "01/06/2025;Pix recebido;1500,00;1500,00\n" +
                "05/06/2025;Pagamento boleto;-300,00;1200,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_virgula_iso_date_nubank() throws Exception {
        // Nubank: vírgula, data ISO, coluna "title" para descrição, "amount" para valor
        String csv = "title,amount,date,category\n" +
                "Uber,\"-25.90\",2025-06-01,Transporte\n" +
                "Salário,\"5000.00\",2025-06-05,Receita\n";
        importar(csv, 2);
    }

    @Test
    void formato_tab_separado() throws Exception {
        // Formato tab (alguns bancos corporativos e exportações do Excel)
        String csv = "Data\tHistórico\tValor\tSaldo\n" +
                "10/06/2025\tTransferência recebida\t2000,00\t2000,00\n" +
                "12/06/2025\tPagamento fornecedor\t-800,00\t1200,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_credito_debito_separados_bradesco() throws Exception {
        // Bradesco internet banking: crédito e débito em colunas separadas
        String csv = "Data;Tipo de Transação;Valor Crédito (R$);Valor Débito (R$);Saldo (R$)\n" +
                "02/06/2025;Ted Recebida;3000,00;;3000,00\n" +
                "03/06/2025;Pagamento;; 500,00;2500,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_pipe_separado() throws Exception {
        String csv = "Data|Descrição|Valor|Saldo\n" +
                "15/06/2025|Venda online|750,00|750,00\n" +
                "16/06/2025|Taxa bancária|-25,00|725,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_com_metadados_no_topo_itau() throws Exception {
        // Itaú exporta linhas de metadados antes do cabeçalho real
        String csv = "Banco Itaú S.A.\n" +
                "Conta Corrente - Extrato\n" +
                "Período: 01/06/2025 a 30/06/2025\n" +
                "\n" +
                "Data;Histórico;Valor do Lançamento;Saldo\n" +
                "04/06/2025;PIX RECEBIDO;1200,00;1200,00\n" +
                "06/06/2025;DEB AUTOMATICO;-150,00;1050,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_ano_2_digitos() throws Exception {
        String csv = "Data;Descrição;Valor\n" +
                "01/06/25;Receita X;2500,00\n" +
                "02/06/25;Despesa Y;-400,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_timestamp_com_hora() throws Exception {
        String csv = "Data;Descrição;Valor\n" +
                "2025-06-10 08:30:00;Depósito;5000,00\n" +
                "2025-06-11 14:00:00;Saque;-1000,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_notacao_contabil_parenteses() throws Exception {
        // Notação contábil: (500,00) = negativo
        String csv = "Data;Descrição;Valor\n" +
                "01/06/2025;Receita;1000,00\n" +
                "02/06/2025;Despesa;(350,00)\n";
        importar(csv, 2);
    }

    @Test
    void formato_sem_cabecalho_deteccao_automatica() throws Exception {
        // Sem cabeçalho — detecção por conteúdo
        String csv = "01/06/2025;Transferência recebida;3000,00;3000,00\n" +
                "05/06/2025;Pagamento boleto;-500,00;2500,00\n";
        importar(csv, 2);
    }

    @Test
    void formato_prefixo_rs_no_valor() throws Exception {
        String csv = "Data;Lançamento;Valor\n" +
                "01/06/2025;Receita;R$ 2.500,00\n" +
                "02/06/2025;Despesa;-R$ 300,00\n";
        importar(csv, 2);
    }

    @Test
    void ignora_linhas_de_saldo_e_rodape() throws Exception {
        String csv = "Data;Lançamento;Valor;Saldo\n" +
                "01/06/2025;Receita;1000,00;1000,00\n" +
                "SALDO ANTERIOR;;0,00;0,00\n" +
                "SALDO TOTAL;;1000,00;1000,00\n";
        // Apenas 1 transação real, saldo anterior e total devem ser ignorados
        importar(csv, 1);
    }

    @Test
    void formato_data_movimento_como_nome_coluna() throws Exception {
        // Coluna "Data Movimento" em vez de "Data"
        String csv = "Data Movimento;Descrição;Valor (R$)\n" +
                "03/06/2025;Receita diversa;800,00\n" +
                "04/06/2025;Custo fixo;-200,00\n";
        importar(csv, 2);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private void importar(String csv, int esperados) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "extrato.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart(BASE)
                        .file(file)
                        .header("Authorization", "Bearer " + ceoToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.importados").value(esperados));
    }
}
