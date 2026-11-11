package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.RecuperacionContrasena;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.RecuperacionContrasenaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
    private static final long TOKEN_TTL_MINUTES = 15;
    private final UsuarioRepository users;
    private final RecuperacionContrasenaRepository tokens;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    private final RefreshSessionService refreshSessions;

    @Autowired
    public PasswordRecoveryService(UsuarioRepository users,
            RecuperacionContrasenaRepository tokens, PasswordEncoder encoder,
            PasswordPolicy passwordPolicy, ObjectProvider<RefreshSessionService> refreshSessions) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = refreshSessions.getIfAvailable();
    }

    public PasswordRecoveryService(UsuarioRepository users,
            RecuperacionContrasenaRepository tokens, PasswordEncoder encoder,
            PasswordPolicy passwordPolicy) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = null;
    }

    public PasswordRecoveryService(UsuarioRepository users,
            RecuperacionContrasenaRepository tokens, PasswordEncoder encoder,
            PasswordPolicy passwordPolicy, RefreshSessionService refreshSessions) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = refreshSessions;
    }

    @Transactional
    public RecoveryRequest request(String email) {
        Usuario user = users.findByCorreo(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !"ACTIVO".equalsIgnoreCase(user.getEstado())) return null;
        String raw = newToken();
        LocalDateTime now = LocalDateTime.now(clock);
        tokens.invalidarPendientes(user.getIdUsuario(), now);
        tokens.save(new RecuperacionContrasena(user, sha256(raw), now, now.plusMinutes(TOKEN_TTL_MINUTES)));
        return new RecoveryRequest(user.getCorreo(), raw);
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        passwordPolicy.validate(newPassword);
        RecuperacionContrasena token = tokens.findLockedByTokenHash(sha256(rawToken))
                .orElseThrow(PasswordRecoveryService::invalidToken);
        LocalDateTime now = LocalDateTime.now(clock);
        if (token.getFechaUso() != null || token.getFechaInvalidacion() != null
                || !token.getFechaExpiracion().isAfter(now)) {
            throw invalidToken();
        }
        Usuario user = token.getUsuario();
        user.setContrasenaHash(encoder.encode(newPassword));
        user.setVersionCredenciales(user.getVersionCredenciales() + 1);
        users.save(user);
        if (refreshSessions != null) refreshSessions.revocarUsuario(user.getIdUsuario());
        token.setFechaUso(now);
        tokens.invalidarPendientes(user.getIdUsuario(), now);
        tokens.save(token);
    }

    public boolean tokenLooksValid(String rawToken) {
        if (!isTokenFormatValid(rawToken)) return false;
        return tokens.findByTokenHash(sha256(rawToken))
                .filter(token -> token.getFechaUso() == null && token.getFechaInvalidacion() == null
                        && token.getFechaExpiracion().isAfter(LocalDateTime.now(clock)))
                .isPresent();
    }

    @Transactional
    public void invalidate(String rawToken) {
        if (!isTokenFormatValid(rawToken)) return;
        tokens.findLockedByTokenHash(sha256(rawToken)).ifPresent(token -> {
            if (token.getFechaUso() == null && token.getFechaInvalidacion() == null) {
                token.setFechaInvalidacion(LocalDateTime.now(clock));
                tokens.save(token);
            }
        });
    }

    private static boolean isTokenFormatValid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("El enlace no es válido o ha caducado. Solicita uno nuevo.");
    }

    public record RecoveryRequest(String email, String token) { }
}
