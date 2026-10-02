package com.orcaai.identity;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends transactional account emails over SMTP.
 *
 * <p>Runs after the transaction commits (never for a rolled-back token) and asynchronously, so the
 * HTTP response time does not reveal whether an email was sent. A failed delivery is logged without
 * details; the user can request a new email.
 */
@Component
class AccountEmails {

    private static final Logger log = LoggerFactory.getLogger(AccountEmails.class);

    private final JavaMailSender mailSender;
    private final AccountProperties properties;

    AccountEmails(JavaMailSender mailSender, AccountProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async(AccountEmailExecutorConfig.EXECUTOR)
    @TransactionalEventListener
    void send(AccountEmailRequested request) {
        try {
            mailSender.send(compose(request));
            log.info("Sent {} email to user {}", request.kind(), request.userId());
        } catch (MailException | MessagingException ex) {
            log.warn("Could not send {} email to user {} ({})",
                    request.kind(), request.userId(), ex.getClass().getSimpleName());
        }
    }

    private MimeMessage compose(AccountEmailRequested request) throws MessagingException {
        Content content = switch (request.kind()) {
            case EMAIL_VERIFICATION -> new Content(
                    "Confirme seu e-mail no Orça Aí",
                    "Recebemos o cadastro da sua empresa no Orça Aí. Para confirmar seu e-mail, acesse o link abaixo.",
                    properties.link("/verify-email", request.token()),
                    "Confirmar e-mail",
                    "O link é válido por " + properties.emailVerificationTtl().toHours() + " horas. Se você não fez este cadastro, ignore esta mensagem.");
            case PASSWORD_RESET -> new Content(
                    "Redefinição de senha do Orça Aí",
                    "Recebemos um pedido para redefinir a senha da sua conta no Orça Aí. Para escolher uma nova senha, acesse o link abaixo.",
                    properties.link("/reset-password", request.token()),
                    "Redefinir senha",
                    "O link é válido por " + properties.passwordResetTtl().toMinutes()
                            + " minutos. Se você não pediu a redefinição, ignore esta mensagem; sua senha continua a mesma.");
            case PASSWORD_CHANGED -> new Content(
                    "Sua senha do Orça Aí foi alterada",
                    "A senha da sua conta foi alterada recentemente.",
                    null,
                    null,
                    "Se foi você, nenhuma ação é necessária. Se não reconhece esta alteração, entre em contato com o suporte.");
        };

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.mailFrom());
        helper.setTo(request.email());
        helper.setSubject(content.subject());
        helper.setText(content.text(), content.html());
        return message;
    }

    /** {@code link} and {@code action} are null for notifications without a link. */
    private record Content(String subject, String intro, String link, String action, String footer) {

        String text() {
            String linkLine = link == null ? "" : link + "\n\n";
            return intro + "\n\n" + linkLine + footer + "\n";
        }

        String html() {
            String linkParagraph = link == null ? ""
                    : "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + HtmlUtils.htmlEscape(action) + "</a></p>";
            return "<p>" + HtmlUtils.htmlEscape(intro) + "</p>" + linkParagraph
                    + "<p>" + HtmlUtils.htmlEscape(footer) + "</p>";
        }
    }
}
