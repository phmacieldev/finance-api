package com.financeiro_api.Auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${app.platform.admin.email:admin@plataforma.com}")
    private String adminEmail;

    @Value("${MAIL_FROM:${spring.mail.username:}}")
    private String mailFrom;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private void setFrom(SimpleMailMessage msg) {
        if (mailFrom != null && !mailFrom.isBlank()) {
            msg.setFrom(mailFrom);
        }
    }

    public void notificarNovaEmpresa(String empresaNome, String empresaCnpj, String responsavelEmail) {
        if (smtpHost == null || smtpHost.isBlank()) {
            log.info("=== NOVA EMPRESA CADASTRADA (SMTP not configured) ===");
            log.info("Empresa: {} | CNPJ: {} | Responsável: {}", empresaNome, empresaCnpj, responsavelEmail);
            log.info("====================================================");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            setFrom(msg);
            msg.setTo(adminEmail);
            msg.setSubject("Nova empresa cadastrada — " + empresaNome);
            msg.setText("""
                    Uma nova empresa se cadastrou na plataforma e aguarda aprovação.

                    Empresa: %s
                    CNPJ: %s
                    Responsável: %s

                    Acesse o painel administrativo para aprovar ou rejeitar o cadastro.
                    """.formatted(empresaNome, empresaCnpj, responsavelEmail));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Falha ao notificar admin sobre nova empresa {}: {}", empresaNome, e.getMessage());
        }
    }

    public void enviarResetSenha(String destinatario, String token) {
        String link = appUrl + "/resetar-senha?token=" + token;

        if (smtpHost == null || smtpHost.isBlank()) {
            log.info("=== PASSWORD RESET (SMTP not configured) ===");
            log.info("To: {}", destinatario);
            log.info("Reset link: {}", link);
            log.info("============================================");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            setFrom(msg);
            msg.setTo(destinatario);
            msg.setSubject("Redefinição de senha — Financeiro SaaS");
            msg.setText("""
                    Olá,

                    Recebemos uma solicitação para redefinir a senha da sua conta.

                    Clique no link abaixo para criar uma nova senha (válido por 1 hora):

                    %s

                    Se você não fez essa solicitação, ignore este e-mail. Sua senha permanece a mesma.
                    """.formatted(link));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Falha ao enviar email de reset para {}: {}", destinatario, e.getMessage());
            log.info("Reset link (fallback): {}", link);
        }
    }

    public void enviarVerificacaoEmail(String destinatario, String token) {
        String link = appUrl + "/verificar-email?token=" + token;

        if (smtpHost == null || smtpHost.isBlank()) {
            // SMTP not configured — log the link so developers can test
            log.info("=== EMAIL VERIFICATION (SMTP not configured) ===");
            log.info("To: {}", destinatario);
            log.info("Verification link: {}", link);
            log.info("================================================");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            setFrom(msg);
            msg.setTo(destinatario);
            msg.setSubject("Confirme seu e-mail — Financeiro SaaS");
            msg.setText("""
                    Olá,

                    Clique no link abaixo para confirmar seu e-mail e ativar sua conta:

                    %s

                    O link expira em 24 horas.

                    Se você não criou uma conta, ignore este e-mail.
                    """.formatted(link));
            mailSender.send(msg);
        } catch (Exception e) {
            log.warn("Falha ao enviar email de verificação para {}: {}", destinatario, e.getMessage());
            log.info("Verification link (fallback): {}", link);
        }
    }
}
