package com.gimnasio.sistemagym.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordRecoveryMailService {
    private final JavaMailSender sender;
    private final String username;
    private final String from;
    private final String frontendUrl;

    public PasswordRecoveryMailService(JavaMailSender sender,
            @Value("${spring.mail.username:}") String username,
            @Value("${sistemagym.mail.from:}") String from,
            @Value("${sistemagym.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.sender = sender;
        this.username = username;
        this.from = from;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    public void send(String email, String rawToken) {
        if (username.isBlank() || from.isBlank()) {
            throw new IllegalStateException("El correo SMTP no está configurado.");
        }
        if (!frontendUrl.matches("^https?://[^\\s<>\"']+$")) {
            throw new IllegalStateException("La URL del frontend no es válida.");
        }
        String link = frontendUrl + "/restablecer-contrasena?token=" + rawToken;
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("Restablece tu contraseña | SistemaGym");
            helper.setText(text(link), html(link));
            sender.send(message);
        } catch (Exception exception) {
            throw new MailDeliveryException("No se pudo enviar el correo de recuperación.", exception);
        }
    }

    private String text(String link) {
        return "Hola,\n\nRecibimos una solicitud para restablecer tu contraseña de SistemaGym. "
                + "Abre este enlace para elegir una nueva contraseña:\n" + link
                + "\n\nEl enlace caduca en 15 minutos y solo puede utilizarse una vez. "
                + "Si no solicitaste este cambio, ignora este mensaje.\n\nSistemaGym";
    }

    private String html(String link) {
        return "<!doctype html><html lang=\"es\"><body style=\"margin:0;background:#f4f7f4;"
                + "font-family:Arial,sans-serif;color:#18231a\"><div style=\"max-width:560px;margin:32px auto;"
                + "padding:32px;background:#fff;border:1px solid #e1e9e1;border-radius:16px\">"
                + "<div style=\"font-size:24px;font-weight:700;color:#087d20\">SistemaGym</div>"
                + "<h1 style=\"font-size:22px;margin:28px 0 12px\">Restablece tu contraseña</h1>"
                + "<p>Recibimos una solicitud para cambiar la contraseña de tu cuenta.</p>"
                + "<p style=\"margin:28px 0\"><a href=\"" + link + "\" style=\"display:inline-block;"
                + "padding:13px 22px;border-radius:9px;background:#087d20;color:#fff;text-decoration:none;"
                + "font-weight:700\">Crear nueva contraseña</a></p><p>Este enlace caduca en <strong>15 minutos</strong> "
                + "y solo puede utilizarse una vez.</p><p>Si no solicitaste el cambio, ignora este mensaje; "
                + "tu contraseña seguirá sin cambios.</p><hr style=\"border:0;border-top:1px solid #e7ece7;margin:28px 0\">"
                + "<p style=\"font-size:12px;color:#657166\">SistemaGym · Gestión integral del gimnasio</p>"
                + "</div></body></html>";
    }

    public static class MailDeliveryException extends RuntimeException {
        public MailDeliveryException(String message, Throwable cause) { super(message, cause); }
    }
}
