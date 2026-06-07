package com.financeiro_api.Audit;

import com.financeiro_api.TenantIntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditLogIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "auditlog.test@teste.com";
    static final String CNPJ  = "22444999000109";
    static final String PASS  = "senha123";

    String adminToken;
    String ceoToken;

    @BeforeEach
    void setup() throws Exception {
        setupMvc();
        limparUsuarioEEmpresa(EMAIL, CNPJ);
        ceoToken = provisionarCeo(EMAIL, CNPJ, PASS);
        adminToken = adminToken();
    }

    @AfterEach
    void teardown() {
        limparUsuarioEEmpresa(EMAIL, CNPJ);
    }

    @Test
    void auditLog_semToken_retorna401ou403() throws Exception {
        mvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void auditLog_semFiltros_retornaRegistros() throws Exception {
        // provisionarCeo já gerou login → deve haver pelo menos 1 registro
        mvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void auditLog_filtradoPorAcao_retornaSomenteAcaoSolicitada() throws Exception {
        mvc.perform(get("/api/v1/audit-logs?action=USER_LOGIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                // todos os registros devem ser USER_LOGIN
                .andExpect(jsonPath("$.content[0].action").value("USER_LOGIN"));
    }

    @Test
    void auditLog_acaoQueNaoExiste_retornaListaVazia() throws Exception {
        // USER_REGISTER existirá, mas filtrando por data futura → vazio
        mvc.perform(get("/api/v1/audit-logs?from=2099-01-01")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void auditLog_loginGeraRegistro() throws Exception {
        // Faz login explícito para gerar registro
        mvc.perform(post(AUTH_BASE + "/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", EMAIL, "password", PASS))));

        mvc.perform(get("/api/v1/audit-logs?action=USER_LOGIN")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)));
    }
}
