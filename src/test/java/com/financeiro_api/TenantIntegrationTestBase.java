package com.financeiro_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeiro_api.Enterprises.domain.EnterpriseStatus;
import com.financeiro_api.Enterprises.repository.EnterpriseRepository;
import com.financeiro_api.Users.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base para testes que precisam de um usuário CEO com empresa aprovada.
 */
public abstract class TenantIntegrationTestBase extends IntegrationTestBase {

    protected static final String AUTH_BASE = "/api/v1/auth";
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected EnterpriseRepository enterpriseRepository;

    protected MockMvc mvc;

    protected void setupMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    protected String json(Object obj) throws Exception {
        return MAPPER.writeValueAsString(obj);
    }

    /**
     * Registra um CEO, verifica o email diretamente no banco e aprova a empresa.
     * Retorna o JWT do CEO.
     */
    protected String provisionarCeo(String email, String cnpj, String password) throws Exception {
        // 1. register
        mvc.perform(post(AUTH_BASE + "/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "enterpriseName", "Empresa " + cnpj,
                        "cnpj", cnpj,
                        "userName", "CEO Teste",
                        "email", email,
                        "password", password
                ))));

        // 2. verificar email direto no banco
        var user = userRepository.findByEmail(email).orElseThrow();
        user.setEmailVerificado(true);
        user.setTokenVerificacao(null);
        user.setTokenVerificacaoExpiracao(null);
        userRepository.save(user);

        // 3. aprovar empresa
        var enterprise = enterpriseRepository.findByCnpj(cnpj).orElseThrow();
        enterprise.setStatus(EnterpriseStatus.ATIVA);
        enterpriseRepository.save(enterprise);

        // 4. login
        var result = mvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
        var body = MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    protected String adminToken() throws Exception {
        var result = mvc.perform(post(AUTH_BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@plataforma.com", "password", "admin@1234"))))
                .andReturn();
        var body = MAPPER.readValue(result.getResponse().getContentAsString(), Map.class);
        return (String) body.get("token");
    }

    protected void limparUsuarioEEmpresa(String email, String cnpj) {
        userRepository.findByEmail(email).ifPresent(u -> {
            userRepository.delete(u);
        });
        enterpriseRepository.findByCnpj(cnpj).ifPresent(e -> {
            userRepository.findAllByEnterprise_IdOrderByNameAsc(e.getId())
                    .forEach(userRepository::delete);
            enterpriseRepository.delete(e);
        });
    }
}
