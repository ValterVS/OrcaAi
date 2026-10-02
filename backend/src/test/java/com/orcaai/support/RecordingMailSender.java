package com.orcaai.support;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Keeps messages in memory instead of talking to an SMTP server. Delivery is asynchronous in the
 * application, so tests wait for messages.
 */
public class RecordingMailSender extends JavaMailSenderImpl {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]+)");

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    public RecordingMailSender() {
        // Same as the application configuration: avoids a host name lookup for the Message-ID.
        getJavaMailProperties().setProperty("mail.from", "nao-responda@orcaai.test");
    }

    @Override
    protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
        if (failing) {
            throw new MailSendException("Connection refused: smtp.internal.example:2525");
        }
        for (MimeMessage message : messages) {
            try {
                // What JavaMailSenderImpl does before transport: finalizes the MIME headers.
                message.saveChanges();
            } catch (MessagingException ex) {
                throw new MailSendException("Invalid message", ex);
            }
            sent.add(message);
        }
    }

    public void failDeliveries(boolean failing) {
        this.failing = failing;
    }

    public List<MimeMessage> messagesTo(String email) {
        return sent.stream().filter(message -> recipient(message).equalsIgnoreCase(email)).toList();
    }

    public MimeMessage awaitMessageTo(String email, int count) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            List<MimeMessage> messages = messagesTo(email);
            if (messages.size() >= count) {
                return messages.get(count - 1);
            }
            sleep(25);
        }
        throw new AssertionError("Expected " + count + " message(s) to " + email);
    }

    /** Waits for the n-th message to the address and returns the token from its link. */
    public String awaitToken(String email, int count) {
        Matcher matcher = TOKEN.matcher(text(awaitMessageTo(email, count)));
        if (!matcher.find()) {
            throw new AssertionError("No token link in the message to " + email);
        }
        return matcher.group(1);
    }

    /** Gives asynchronous delivery time to happen, then returns what was delivered. */
    public List<MimeMessage> settledMessagesTo(String email) {
        sleep(500);
        return messagesTo(email);
    }

    public static String text(MimeMessage message) {
        try {
            return findPlainText(message).orElseThrow(() -> new AssertionError("No text/plain part"));
        } catch (MessagingException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String subject(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Optional<String> findPlainText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return Optional.of((String) part.getContent());
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                Optional<String> text = findPlainText(multipart.getBodyPart(i));
                if (text.isPresent()) {
                    return text;
                }
            }
        }
        return Optional.empty();
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getRecipients(Message.RecipientType.TO)[0].toString();
        } catch (MessagingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
