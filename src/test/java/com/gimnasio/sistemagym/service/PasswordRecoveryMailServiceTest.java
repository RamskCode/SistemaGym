package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryMailServiceTest {
    @Mock JavaMailSender sender;

    @Test
    void construyeCorreoConVersionHtmlYTextoYEnlaceDeUsoUnico() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        PasswordRecoveryMailService service = new PasswordRecoveryMailService(
                sender, "gym@example.test", "gym@example.test", "https://gym.example.test");

        service.send("persona@example.test", "t".repeat(43));

        assertTrue(message.getSubject().contains("SistemaGym"));
        assertTrue(message.getAllRecipients()[0].toString().contains("persona@example.test"));
        String bodies = collectText(message.getContent());
        assertTrue(bodies.contains("15 minutos"));
        assertTrue(bodies.contains("https://gym.example.test/restablecer-contrasena?token="));
        verify(sender).send(message);
    }

    private static String collectText(Object content) throws Exception {
        if (content instanceof Multipart multipart) {
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < multipart.getCount(); index++) {
                text.append(collectText(multipart.getBodyPart(index).getContent()));
            }
            return text.toString();
        }
        return content == null ? "" : content.toString();
    }
}
