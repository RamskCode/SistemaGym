package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.dto.RestablecerContrasenaRequest;
import com.gimnasio.sistemagym.dto.SolicitarRecuperacionRequest;
import com.gimnasio.sistemagym.dto.ValidarRecuperacionRequest;
import com.gimnasio.sistemagym.service.PasswordRecoveryMailService;
import com.gimnasio.sistemagym.service.PasswordRecoveryRateLimiter;
import com.gimnasio.sistemagym.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/password-recovery")
public class PasswordRecoveryController {
    private static final Map<String, String> GENERIC_RESPONSE = Map.of("mensaje",
            "Si el correo está registrado, recibirás un enlace para restablecer tu contraseña.");
    private final PasswordRecoveryService recovery;
    private final PasswordRecoveryMailService mail;
    private final PasswordRecoveryRateLimiter limiter;

    public PasswordRecoveryController(PasswordRecoveryService recovery,
            PasswordRecoveryMailService mail, PasswordRecoveryRateLimiter limiter) {
        this.recovery = recovery;
        this.mail = mail;
        this.limiter = limiter;
    }

    @PostMapping("/solicitar")
    public ResponseEntity<Map<String, String>> request(
            @Valid @RequestBody SolicitarRecuperacionRequest request,
            HttpServletRequest servletRequest) {
        String correo = request.correo().trim();
        if (!limiter.allow(correo, servletRequest.getRemoteAddr())) {
            return ResponseEntity.accepted().body(GENERIC_RESPONSE);
        }
        PasswordRecoveryService.RecoveryRequest issued = recovery.request(correo);
        if (issued != null) {
            try {
                mail.send(issued.email(), issued.token());
            } catch (RuntimeException ignored) {
                // The response never claims delivery and never reveals account existence.
                recovery.invalidate(issued.token());
            }
        }
        return ResponseEntity.accepted().body(GENERIC_RESPONSE);
    }

    @PostMapping("/validar")
    public ResponseEntity<Void> validate(@Valid @RequestBody ValidarRecuperacionRequest request) {
        return recovery.tokenLooksValid(request.token())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/restablecer")
    public ResponseEntity<Void> reset(@Valid @RequestBody RestablecerContrasenaRequest request) {
        recovery.reset(request.token(), request.contrasena());
        return ResponseEntity.noContent().build();
    }
}
