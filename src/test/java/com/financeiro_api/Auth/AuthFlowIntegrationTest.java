package com.financeiro_api.Auth;

import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.TenantIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthFlowIntegrationTest extends TenantIntegrationTestBase {

    static final String EMAIL = "authflow.test@teste.com";
    static final String CNPJ  = "11111111000191";
    static final String PASS  = "senha123";

    @BeforeEach
    void setup() throws Exception {
        setupMvc();
        limparUsuarioEEmpresa(EMAIL, CNPJ);
    }

    @AfterEach
    void teardown() {
        limparUsuarioEEmpresa(EMAIL, CNPJ);
    }

    // ── verificar-email ───────────────────────────────────────────────────────

    @Test
    void verificarEmail_tokenValido_retornaJwtEAprovaDados() throws Exception {
        // 1. register (sem provisionarCeo — queremos o token real)
        mvc.perform(post(AUTH_BASE + "/register")
                .contentType(APPLICATION_JSON)
                .content(json(Map.of(
                        "enterpriseName", "Empresa AuthFlow",
                        "cnpj", CNPJ,
                        "userName", "CEO AuthFlow",
                        "email", EMAIL,
                        "password", PASS
                ))));

        var user = userRepository.findByEmailWithEnterprise(EMAIL).orElseThrow();
        String token = user.getTokenVerificacao();

        // aprovar empresa antes de verificar email (necessário para o login pós-verificação)
        var enterprise = user.getEnterprise();
        enterprise.setStatus(EnterpriseStatus.ATIVA);
        enterpriseRepository.save(enterprise);

        // 2. verificar email — deve retornar JWT imediatamente
        mvc.perform(get(AUTH_BASE + "/verificar-email").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    void verificarEmail_tokenInvalido_retorna4xx() throws Exception {
        mvc.perform(get(AUTH_BASE + "/verificar-email").param("token", "token-invalido-qualquer"))
                .andExpect(status().is4xxClientError());
    }

    // ── reenviar-verificacao ──────────────────────────────────────────────────

    @Test
    void reenviarVerificacao_usuarioNaoVerificado_retorna204() throws Exception {
        mvc.perform(post(AUTH_BASE + "/register")
                .contentType(APPLICATION_JSON)
                .content(json(Map.of(
                        "enterpriseName", "Empresa AuthFlow",
                        "cnpj", CNPJ,
                        "userName", "CEO AuthFlow",
                        "email", EMAIL,
                        "password", PASS
                ))));

        mvc.perform(post(AUTH_BASE + "/reenviar-verificacao").param("email", EMAIL))
                .andExpect(status().isNoContent());
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_cookieValido_retornaNovoJwt() throws Exception {
        provisionarCeo(EMAIL, CNPJ, PASS);

        // login para obter refresh token no response body
        var loginResult = mvc.perform(post(AUTH_BASE + "/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASS))))
                .andReturn();

        var body = MAPPER.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String refreshToken = (String) body.get("refreshToken");

        mvc.perform(post(AUTH_BASE + "/refresh")
                        .cookie(new Cookie("financeiro_refresh", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void refresh_semCookie_retorna401() throws Exception {
        mvc.perform(post(AUTH_BASE + "/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_tokenInvalido_retorna401() throws Exception {
        mvc.perform(post(AUTH_BASE + "/refresh")
                        .cookie(new Cookie("financeiro_refresh", "token-invalido")))
                .andExpect(status().isUnauthorized());
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_revogaRefreshToken() throws Exception {
        provisionarCeo(EMAIL, CNPJ, PASS);

        var loginResult = mvc.perform(post(AUTH_BASE + "/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASS))))
                .andReturn();

        var body = MAPPER.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String refreshToken = (String) body.get("refreshToken");

        // logout revoga o token
        mvc.perform(post(AUTH_BASE + "/logout")
                        .cookie(new Cookie("financeiro_refresh", refreshToken)))
                .andExpect(status().isNoContent());

        // refresh com o token revogado deve falhar
        mvc.perform(post(AUTH_BASE + "/refresh")
                        .cookie(new Cookie("financeiro_refresh", refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_semCookie_retorna204SemErro() throws Exception {
        // logout sem cookie deve ser silencioso (idempotente)
        mvc.perform(post(AUTH_BASE + "/logout"))
                .andExpect(status().isNoContent());
    }
}
