package com.gimnasio.sistemagym.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class InvitacionMailService {
    private final JavaMailSender sender;
    private final String username;
    private final String from;
    private final String frontendUrl;

    public InvitacionMailService(JavaMailSender sender,
            @Value("${spring.mail.username:}") String username,
            @Value("${sistemagym.mail.from:}") String from,
            @Value("${sistemagym.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.sender = sender; this.username = username; this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void send(String email, String name, String rawToken, long ttlHours) {
        if (username.isBlank() || from.isBlank()
                || !frontendUrl.matches("^https?://[^\\s<>\"']+$")) {
            throw new IllegalStateException("El correo de invitación no está configurado.");
        }
        String link = frontendUrl + "/activar-cuenta?token=" + rawToken;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from); helper.setTo(email);
            helper.setSubject("Activa tu cuenta | SistemaGym");
            helper.setText("Hola " + name + ",\n\nCrea la contraseña de tu cuenta de SistemaGym aquí:\n"
                    + link + "\n\nEl enlace caduca en " + ttlHours
                    + " horas y solo puede usarse una vez.\n\nSistemaGym",
                    "<!doctype html><html lang=\"es\"><body style=\"font-family:Arial,sans-serif;color:#18231a\"><main style=\"max-width:560px;margin:32px auto;padding:32px;border:1px solid #e1e9e1;border-radius:16px\"><h1 style=\"color:#087d20\">Activa tu cuenta SistemaGym</h1><p>Hola "
                    + escape(name) + ", crea una contraseña segura para acceder al portal del personal.</p><p><a href=\""
                    + link + "\" style=\"display:inline-block;padding:13px 22px;border-radius:9px;background:#087d20;color:#fff;text-decoration:none;font-weight:bold\">Crear contraseña</a></p><p>Este enlace caduca en "
                    + ttlHours + " horas y solo puede utilizarse una vez.</p></main></body></html>");
            sender.send(message);
        } catch (Exception exception) {
            throw new PasswordRecoveryMailService.MailDeliveryException(
                    "No se pudo enviar la invitación.", exception);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
