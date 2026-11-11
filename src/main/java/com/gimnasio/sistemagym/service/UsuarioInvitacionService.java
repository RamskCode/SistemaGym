package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.InvitacionUsuario;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.InvitacionUsuarioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UsuarioInvitacionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_INVITACIONES_24H = 3;
    private final UsuarioRepository users;
    private final InvitacionUsuarioRepository invitations;
    private final InvitacionMailService mail;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final TransactionTemplate transaction;
    private final long ttlHours;

    public UsuarioInvitacionService(UsuarioRepository users,
            InvitacionUsuarioRepository invitations, InvitacionMailService mail,
            PasswordEncoder encoder, PasswordPolicy policy,
            PlatformTransactionManager manager,
            @Value("${sistemagym.invitation.ttl-hours:48}") long ttlHours) {
        this.users = users; this.invitations = invitations; this.mail = mail;
        this.encoder = encoder; this.policy = policy;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        if (ttlHours < 1 || ttlHours > 168) throw new IllegalArgumentException("TTL de invitación inválido.");
        this.ttlHours = ttlHours;
    }

    /** Persists the token before SMTP. SMTP failure therefore remains recoverable via resend. */
    public Delivery sendInvitation(long userId) {
        Prepared prepared = transaction.execute(status -> prepare(userId));
        if (prepared == null) throw new IllegalStateException("No se pudo preparar la invitación.");
        try {
            mail.send(prepared.email(), prepared.name(), prepared.rawToken(), ttlHours);
            return new Delivery(true, "La invitación se envió correctamente.");
        } catch (RuntimeException mailFailure) {
            return new Delivery(false, "La cuenta quedó pendiente. No se confirmó el envío; puedes reenviar la invitación.");
        }
    }

    private Prepared prepare(long userId) {
        Usuario user = users.findLockedById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el usuario."));
        if (!"PENDIENTE".equals(user.getEstado())) {
            throw new ConflictoException("Solo se puede invitar a cuentas pendientes de activación.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (invitations.countByUsuarioIdUsuarioAndFechaCreacionAfter(userId, now.minusHours(24))
                >= MAX_INVITACIONES_24H) {
            throw new ConflictoException("Se alcanzó el límite de invitaciones. Inténtalo más tarde.");
        }
        String raw = newToken();
        invitations.invalidateOpen(userId, now);
        invitations.saveAndFlush(new InvitacionUsuario(user, sha256(raw), now, now.plusHours(ttlHours)));
        return new Prepared(user.getCorreo(), user.getNombres(), raw);
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String raw) {
        if (!validFormat(raw)) return false;
        return invitations.findByTokenHash(sha256(raw))
                .filter(i -> i.getFechaUso() == null && i.getFechaInvalidacion() == null
                        && i.getFechaExpiracion().isAfter(LocalDateTime.now())
                        && "PENDIENTE".equals(i.getUsuario().getEstado()))
                .isPresent();
    }

    @Transactional
    public void activate(String rawToken, String rawPassword) {
        if (!validFormat(rawToken)) throw invalidToken();
        policy.validate(rawPassword);
        InvitacionUsuario invitation = invitations.findLockedByTokenHash(sha256(rawToken))
                .orElseThrow(UsuarioInvitacionService::invalidToken);
        LocalDateTime now = LocalDateTime.now();
        Usuario user = invitation.getUsuario();
        if (invitation.getFechaUso() != null || invitation.getFechaInvalidacion() != null
                || !invitation.getFechaExpiracion().isAfter(now)
                || !"PENDIENTE".equals(user.getEstado())) throw invalidToken();
        user.setContrasenaHash(encoder.encode(rawPassword));
        user.setEstado("ACTIVO");
        user.setVersionCredenciales(user.getVersionCredenciales() + 1);
        users.saveAndFlush(user);
        invitation.setFechaUso(now);
        invitations.invalidateOpen(user.getIdUsuario(), now);
        invitations.save(invitation);
    }

    private static String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static boolean validFormat(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.US_ASCII))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("El enlace no es válido, ya se usó o caducó. Solicita una nueva invitación.");
    }
    private record Prepared(String email, String name, String rawToken) { }
    public record Delivery(boolean sent, String message) { }
}
