package com.gimnasio.sistemagym.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenService(
            SecretKey signingKey,
            @Value("${app.jwt.expiration-ms:900000}") long expirationMs) {
        if (expirationMs < 60_000 || expirationMs > 86_400_000) {
            throw new IllegalStateException(
                    "La expiración JWT debe estar entre 1 minuto y 24 horas.");
        }
        this.signingKey = signingKey;
        this.expirationMs = expirationMs;
    }

    public String generar(UserDetails usuario) {
        return generar(usuario, 0L);
    }

    public String generar(UserDetails usuario, long versionCredenciales) {
        return generar(usuario, versionCredenciales, null);
    }

    public String generar(UserDetails usuario, long versionCredenciales, String sessionId) {
        Instant ahora = Instant.now();
        List<String> roles = usuario.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        var builder = Jwts.builder()
                .subject(usuario.getUsername())
                .claim("roles", roles)
                .claim("credentialVersion", versionCredenciales)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expirationMs)));
        if (sessionId != null) builder.claim("sid", sessionId);
        return builder.signWith(signingKey).compact();
    }

    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
