package com.qorbit.engine.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envia e-mails transacionais (verificação, reset de senha).
 *
 * Se SMTP não estiver configurado (qorbit.mail.enabled=false),
 * imprime o conteúdo no log — útil para desenvolvimento local.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${qorbit.mail.enabled:false}")
    private boolean mailEnabled;

    // Remetente: usa o username SMTP quando disponível (obrigatório para Gmail)
    @Value("${qorbit.mail.from:${spring.mail.username:noreply@qorbit.local}}")
    private String from;

    // Username SMTP — se configurado, habilita envio mesmo sem qorbit.mail.enabled=true
    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${qorbit.app.base-url:http://localhost:8080}")
    private String baseUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/auth/verify-email?token=" + token;
        String body = """
            Bem-vindo ao Qorbit!

            Confirme seu e-mail acessando o link abaixo:
            %s

            O link expira em 24 horas.

            Se você não criou uma conta, ignore este e-mail.
            """.formatted(link);

        send(to, "Confirme seu e-mail — Qorbit", body);
    }

    public void sendPasswordResetEmail(String to, String token) {
        String link = baseUrl + "/auth/reset-password?token=" + token;
        String body = """
            Recebemos uma solicitação de redefinição de senha para sua conta Qorbit.

            Acesse o link abaixo para criar uma nova senha (válido por 15 minutos):
            %s

            Se você não solicitou a redefinição, ignore este e-mail.
            Sua senha permanece a mesma.
            """.formatted(link);

        send(to, "Redefinição de senha — Qorbit", body);
    }

    private void send(String to, String subject, String body) {
        boolean smtpConfigured = smtpUsername != null && !smtpUsername.isBlank();
        if (!mailEnabled && !smtpConfigured) {
            log.info("[Email DEV] Para: {} | Assunto: {} | Corpo: {}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(smtpConfigured ? smtpUsername : from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("[Email] Enviado para: {}", to);
        } catch (Exception e) {
            log.error("[Email] Falha ao enviar para {}: {}", to, e.getMessage(), e);
        }
    }
}
