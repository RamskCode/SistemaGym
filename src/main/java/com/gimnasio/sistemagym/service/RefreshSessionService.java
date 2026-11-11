package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.SesionRefresh;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.SesionRefreshRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshSessionService {
    public static final Duration ABSOLUTE_LIFETIME = Duration.ofHours(12);
    private final SesionRefreshRepository sesiones;
    private final UsuarioRepository usuarios;
    private final JwtTokenService jwt;
    private final CustomUserDetailsService userDetails;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(SesionRefreshRepository sesiones, UsuarioRepository usuarios,
            JwtTokenService jwt, CustomUserDetailsService userDetails) {
        this.sesiones = sesiones;
        this.usuarios = usuarios;
        this.jwt = jwt;
        this.userDetails = userDetails;
    }

    @Transactional
    public Tokens crear(Usuario usuario) {
        sesiones.eliminarExpiradas(Instant.now());
        String refresh = randomToken();
        String csrf = randomToken();
        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        SesionRefresh session = new SesionRefresh(sessionId, usuario,
                digest(refresh), digest(csrf), usuario.getVersionCredenciales(), now,
                now.plus(ABSOLUTE_LIFETIME));
        sesiones.save(session);
        return new Tokens(jwt.generar(asUserDetails(usuario), usuario.getVersionCredenciales(), sessionId),
                refresh, csrf, jwt.getExpirationMs(), ABSOLUTE_LIFETIME);
    }

    @Transactional
    public Tokens renovar(String refresh, String csrf) {
        if (refresh == null || csrf == null || refresh.length() > 128 || csrf.length() > 128) {
            throw new SesionInvalidaException();
        }
        SesionRefresh session = sesiones.buscarBloqueadaPorHash(digest(refresh))
                .orElseThrow(SesionInvalidaException::new);
        if (!MessageDigest.isEqual(session.getCsrfHash().getBytes(StandardCharsets.US_ASCII),
                digest(csrf).getBytes(StandardCharsets.US_ASCII))
                || !Instant.now().isBefore(session.getExpiraEn())) {
            throw new SesionInvalidaException();
        }
        Usuario usuario = session.getUsuario();
        long actual = usuarios.findCredentialVersionByCorreo(usuario.getCorreo())
                .orElse(Long.MIN_VALUE);
        if (actual != session.getVersionCredenciales()) throw new SesionInvalidaException();
        UserDetails principal;
        try {
            principal = userDetails.loadUserByUsername(usuario.getCorreo());
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
            throw new SesionInvalidaException();
        }
        String nextRefresh = randomToken();
        String nextCsrf = randomToken();
        session.rotar(digest(nextRefresh), digest(nextCsrf));
        sesiones.saveAndFlush(session);
        return new Tokens(jwt.generar(principal, actual, session.getId()), nextRefresh, nextCsrf,
                jwt.getExpirationMs(), Duration.between(Instant.now(), session.getExpiraEn()));
    }

    @Transactional
    public void revocar(String refresh, String csrf) {
        if (refresh == null || csrf == null || refresh.length() > 128 || csrf.length() > 128) return;
        sesiones.buscarBloqueadaPorHash(digest(refresh)).ifPresent(session -> {
            if (MessageDigest.isEqual(session.getCsrfHash().getBytes(StandardCharsets.US_ASCII),
                    digest(csrf).getBytes(StandardCharsets.US_ASCII))) {
                sesiones.delete(session);
            }
        });
    }

    @Transactional
    public void revocarUsuario(Long usuarioId) {
        sesiones.revocarTodasDeUsuario(usuarioId);
    }

    private UserDetails asUserDetails(Usuario usuario) {
        return userDetails.loadUserByUsername(usuario.getCorreo());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Tokens(String accessToken, String refreshToken, String csrfToken,
            long accessLifetimeMs, Duration refreshLifetime) { }

    public static class SesionInvalidaException extends RuntimeException { }
}
