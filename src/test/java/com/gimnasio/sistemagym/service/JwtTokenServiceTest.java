package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

class JwtTokenServiceTest {

    private SecretKey key;
    private JwtTokenService service;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(new byte[32]);
        service = new JwtTokenService(key, 900_000);
    }

    @Test
    void generaYValidaTokenConIdentidadYAutoridad() {
        var user = User.withUsername("nutri@example.test")
                .password("not-in-token")
                .roles("NUTRICIONISTA")
                .build();

        Claims claims = service.validar(service.generar(user));

        assertEquals("nutri@example.test", claims.getSubject());
        assertEquals("ROLE_NUTRICIONISTA", ((java.util.List<?>) claims.get("roles")).get(0));
        assertEquals(900_000L, service.getExpirationMs());
    }

    @Test
    void rechazaTokenExpirado() {
        String token = Jwts.builder().subject("user@example.test")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(key).compact();

        assertThrows(io.jsonwebtoken.ExpiredJwtException.class,
                () -> service.validar(token));
    }

    @Test
    void rechazaFirmaInvalidaYTokenMalformado() {
        byte[] otherKeyBytes = new byte[32];
        java.util.Arrays.fill(otherKeyBytes, (byte) 1);
        SecretKey otherKey = Keys.hmacShaKeyFor(otherKeyBytes);
        String token = Jwts.builder().subject("user@example.test")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(otherKey).compact();

        assertThrows(io.jsonwebtoken.JwtException.class, () -> service.validar(token));
        assertThrows(io.jsonwebtoken.JwtException.class,
                () -> service.validar("not-a-jwt"));
    }

    @Test
    void rechazaDuracionFueraDelRangoSeguro() {
        assertThrows(IllegalStateException.class,
                () -> new JwtTokenService(key, 10));
    }
}
