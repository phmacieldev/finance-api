package com.financeiro_api.Auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.IntegrationTestBase;
import com.financeiro_api.Users.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthIntegrationTest extends IntegrationTestBase {

    MockMvc mvc;

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EnterpriseRepository enterpriseRepository;

    final ObjectMapper mapper = new ObjectMapper();

    static final String TEST_EMAIL    = "teste.integracao@teste.com";
    static final String TEST_CNPJ     = "11222333000181";
    static final String BASE          = "/api/v1/auth";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        cleanupTestData();
    }

    @AfterEach
    void teardown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        userRepository.findByEmail(TEST_EMAIL).ifPresent(u -> {
            var enterprise = u.getEnterprise();
            userRepository.delete(u);
            if (enterprise != null) {
                enterpriseRepository.delete(enterprise);
            }
        });
        enterpriseRepository.findByCnpj(TEST_CNPJ).ifPresent(e -> {
            userRepository.findAllByEnterprise_IdOrderByNameAsc(e.getId())
                    .forEach(userRepository::delete);
            enterpriseRepository.delete(e);
        });
    }

    private String json(Object obj) throws Exception {
        return mapper.writeValueAsString(obj);
    }

    private String adminToken() throws Exception {
        var result = mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com", "password", "admin@1234"))))
                .andReturn();
        var body = mapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    private void registerTestUser() throws Exception {
        mvc.perform(post(BASE + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "enterpriseName", "Empresa Integração Teste",
                        "cnpj", TEST_CNPJ,
                        "userName", "Usuário Teste",
                        "email", TEST_EMAIL,
                        "password", "senha123"
                ))));
    }

    // ── register ──────────────────────────────────────────────────────────────

    @Test
    void register_retorna201() throws Exception {
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "enterpriseName", "Empresa Integração Teste",
                                "cnpj", TEST_CNPJ,
                                "userName", "Usuário Teste",
                                "email", TEST_EMAIL,
                                "password", "senha123"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(TEST_EMAIL));
    }

    @Test
    void register_senhaCurta_retorna400() throws Exception {
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "enterpriseName", "Empresa Teste",
                                "cnpj", TEST_CNPJ,
                                "userName", "Usuário Teste",
                                "email", TEST_EMAIL,
                                "password", "abc1"
                        ))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_senhaSemNumero_retorna400() throws Exception {
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "enterpriseName", "Empresa Teste",
                                "cnpj", TEST_CNPJ,
                                "userName", "Usuário Teste",
                                "email", TEST_EMAIL,
                                "password", "senhasemnumero"
                        ))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_cnpjInvalido_retorna422() throws Exception {
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "enterpriseName", "Empresa Teste",
                                "cnpj", "11111111111111",
                                "userName", "Usuário Teste",
                                "email", TEST_EMAIL,
                                "password", "senha123"
                        ))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void register_emailDuplicado_retorna409() throws Exception {
        registerTestUser();
        mvc.perform(post(BASE + "/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "enterpriseName", "Outra Empresa",
                                "cnpj", "99888777000100",
                                "userName", "Maria",
                                "email", TEST_EMAIL,
                                "password", "senha123"
                        ))))
                .andExpect(status().isConflict());
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_admin_retorna200_comRolePlatformAdmin() throws Exception {
        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com", "password", "admin@1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_senhaErrada_retorna401() throws Exception {
        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com", "password", "senhaerrada"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_emailNaoVerificado_retorna4xx() throws Exception {
        registerTestUser();
        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TEST_EMAIL, "password", "senha123"))))
                .andExpect(status().is4xxClientError());
    }

    // ── esqueci-senha ──────────────────────────────────────────────────────────

    @Test
    void esqueciSenha_emailExistente_retorna204() throws Exception {
        mvc.perform(post(BASE + "/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void esqueciSenha_emailInexistente_retorna204_semVazarInfo() throws Exception {
        mvc.perform(post(BASE + "/esqueci-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "naoexiste@nenhum.com"))))
                .andExpect(status().isNoContent());
    }

    // ── resetar-senha ──────────────────────────────────────────────────────────

    @Test
    void resetarSenha_tokenInvalido_retorna401() throws Exception {
        mvc.perform(post(BASE + "/resetar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", "tokeninvalido", "novaSenha", "nova1234"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetarSenha_fluxoCompleto() throws Exception {
        // 1. solicitar reset para o admin
        mvc.perform(post(BASE + "/esqueci-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "admin@plataforma.com"))));

        // 2. pegar token diretamente no banco
        var user = userRepository.findByEmail("admin@plataforma.com").orElseThrow();
        var token = user.getTokenResetSenha();
        assertThat(token).isNotNull();

        // 3. resetar senha
        mvc.perform(post(BASE + "/resetar-senha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "novaSenha", "novasenha456"))))
                .andExpect(status().isNoContent());

        // 4. login com nova senha
        mvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com", "password", "novasenha456"))))
                .andExpect(status().isOk());

        // 5. restaurar senha original
        mvc.perform(post(BASE + "/esqueci-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "admin@plataforma.com"))));
        var user2 = userRepository.findByEmail("admin@plataforma.com").orElseThrow();
        mvc.perform(post(BASE + "/resetar-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", user2.getTokenResetSenha(), "novaSenha", "admin@1234"))));
    }

    // ── admin endpoints ────────────────────────────────────────────────────────

    @Test
    void adminEmpresas_semToken_retorna401ou403() throws Exception {
        mvc.perform(get("/api/v1/admin/empresas"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void adminEmpresas_comTokenAdmin_retorna200() throws Exception {
        var token = adminToken();
        mvc.perform(get("/api/v1/admin/empresas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ── DELETE /me ────────────────────────────────────────────────────────────

    @Test
    void deletarConta_semAutenticacao_retorna401ou403() throws Exception {
        mvc.perform(delete("/api/v1/me"))
                .andExpect(status().is4xxClientError());
    }
}
