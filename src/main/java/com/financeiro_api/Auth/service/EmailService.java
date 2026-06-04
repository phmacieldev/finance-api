package com.financeiro_api.Auth.service;

import com.financeiro_api.shared.LogMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String RESEND_API = "https://api.resend.com/emails";

    private final RestClient restClient = RestClient.create();

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${MAIL_FROM:}")
    private String mailFrom;

    @Value("${app.platform.admin.email:admin@plataforma.com}")
    private String adminEmail;

    private boolean canSend() {
        return resendApiKey != null && !resendApiKey.isBlank()
                && mailFrom != null && !mailFrom.isBlank();
    }

    private void send(String to, String subject, String text) {
        try {
            restClient.post()
                    .uri(RESEND_API)
                    .header("Authorization", "Bearer " + resendApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", mailFrom,
                            "to", List.of(to),
                            "subject", subject,
                            "text", text
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Falha ao enviar email para {}: {}", LogMask.email(to), e.getMessage());
        }
    }

    @Async
    public void notificarNovaEmpresa(String empresaNome, String empresaCnpj, String responsavelEmail) {
        if (!canSend()) {
            log.info("=== NOVA EMPRESA CADASTRADA (RESEND_API_KEY not configured) ===");
            log.info("Empresa: {} | CNPJ: {} | Responsável: {}",
                    empresaNome, LogMask.cnpj(empresaCnpj), LogMask.email(responsavelEmail));
            log.info("==============================================================");
            return;
        }
        send(adminEmail,
                "Nova empresa cadastrada — " + empresaNome,
                """
                Uma nova empresa se cadastrou na plataforma e aguarda aprovação.

                Empresa: %s
                CNPJ: %s
                Responsável: %s

                Acesse o painel administrativo para aprovar ou rejeitar o cadastro.
                """.formatted(empresaNome, empresaCnpj, responsavelEmail));
    }

    @Async
    public void enviarResetSenha(String destinatario, String token) {
        String link = appUrl + "/resetar-senha?token=" + token;
        if (!canSend()) {
            log.info("=== PASSWORD RESET (RESEND_API_KEY not configured) ===");
            log.info("To: {} | Link: {}", LogMask.email(destinatario), link);
            log.info("=====================================================");
            return;
        }
        send(destinatario,
                "Redefinição de senha — Financeiro SaaS",
                """
                Olá,

                Recebemos uma solicitação para redefinir a senha da sua conta.

                Clique no link abaixo para criar uma nova senha (válido por 1 hora):

                %s

                Se você não fez essa solicitação, ignore este e-mail.
                """.formatted(link));
    }

    @Async
    public void enviarVerificacaoEmail(String destinatario, String token) {
        String link = appUrl + "/verificar-email?token=" + token;
        if (!canSend()) {
            log.info("=== EMAIL VERIFICATION (RESEND_API_KEY not configured) ===");
            log.info("To: {} | Link: {}", LogMask.email(destinatario), link);
            log.info("==========================================================");
            return;
        }
        send(destinatario,
                "Confirme seu e-mail — Financeiro SaaS",
                """
                Olá,

                Clique no link abaixo para confirmar seu e-mail e ativar sua conta:

                %s

                O link expira em 24 horas.

                Se você não criou uma conta, ignore este e-mail.
                """.formatted(link));
    }
}
