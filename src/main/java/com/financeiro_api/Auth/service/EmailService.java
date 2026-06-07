package com.financeiro_api.Auth.service;

import com.financeiro_api.shared.LogMask;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    @Value("${app.platform.admin.email:admin@plataforma.com}")
    private String adminEmail;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    private boolean canSend() {
        return mailUsername != null && !mailUsername.isBlank();
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUsername);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            log.warn("Falha ao enviar email para {}: {}", LogMask.email(to), e.getMessage());
        }
    }

    @Async
    public void notificarNovaEmpresa(String empresaNome, String identificador, String responsavelEmail) {
        if (!canSend()) {
            log.info("=== NOVA EMPRESA CADASTRADA (MAIL_USERNAME não configurado) ===");
            log.info("Empresa: {} | Identificador: {} | Responsável: {}",
                    empresaNome, LogMask.cnpj(identificador), LogMask.email(responsavelEmail));
            return;
        }
        send(adminEmail,
                "Nova empresa cadastrada — " + empresaNome,
                """
                <p>Uma nova empresa se cadastrou e aguarda aprovação.</p>
                <ul>
                  <li><strong>Empresa:</strong> %s</li>
                  <li><strong>CNPJ/CPF:</strong> %s</li>
                  <li><strong>Responsável:</strong> %s</li>
                </ul>
                <p>Acesse o painel administrativo para aprovar ou rejeitar.</p>
                """.formatted(empresaNome, identificador != null ? identificador : "—", responsavelEmail));
    }

    @Async
    public void enviarResetSenha(String destinatario, String token) {
        String link = appUrl + "/resetar-senha?token=" + token;
        if (!canSend()) {
            log.info("=== PASSWORD RESET (MAIL_USERNAME não configurado) ===");
            log.info("To: {} | Link: {}", LogMask.email(destinatario), link);
            return;
        }
        send(destinatario,
                "Redefinição de senha — Financeiro",
                """
                <p>Olá,</p>
                <p>Recebemos uma solicitação para redefinir a senha da sua conta.</p>
                <p>
                  <a href="%s" style="display:inline-block;padding:10px 20px;background:#2563eb;color:#fff;border-radius:6px;text-decoration:none;font-weight:bold;">
                    Redefinir senha
                  </a>
                </p>
                <p style="color:#6b7280;font-size:13px;">
                  O link é válido por 1 hora. Se você não fez essa solicitação, ignore este e-mail.
                </p>
                """.formatted(link));
    }

    @Async
    public void enviarVerificacaoEmail(String destinatario, String token) {
        String link = appUrl + "/verificar-email?token=" + token;
        if (!canSend()) {
            log.info("=== EMAIL VERIFICATION (MAIL_USERNAME não configurado) ===");
            log.info("To: {} | Link: {}", LogMask.email(destinatario), link);
            return;
        }
        send(destinatario,
                "Confirme seu e-mail — Financeiro",
                """
                <p>Olá,</p>
                <p>Clique no botão abaixo para confirmar seu e-mail e ativar sua conta:</p>
                <p>
                  <a href="%s" style="display:inline-block;padding:10px 20px;background:#2563eb;color:#fff;border-radius:6px;text-decoration:none;font-weight:bold;">
                    Confirmar e-mail
                  </a>
                </p>
                <p style="color:#6b7280;font-size:13px;">
                  O link expira em 24 horas. Se você não criou uma conta, ignore este e-mail.
                </p>
                """.formatted(link));
    }
}
