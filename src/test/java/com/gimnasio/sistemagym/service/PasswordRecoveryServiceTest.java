package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gimnasio.sistemagym.entity.RecuperacionContrasena;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.RecuperacionContrasenaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {
    @Mock UsuarioRepository users;
    @Mock RecuperacionContrasenaRepository tokens;
    @Mock RefreshSessionService refreshSessions;

    private PasswordRecoveryService service;

    @BeforeEach
    void setup() {
        service = new PasswordRecoveryService(users, tokens,
                new BCryptPasswordEncoder(), new PasswordPolicy(), refreshSessions);
    }

    @Test
    void creaTokenAleatorioSoloComoHashYConCaducidadDeQuinceMinutos() {
        Usuario user = activeUser();
        when(users.findByCorreo("persona@example.test")).thenReturn(Optional.of(user));

        var first = service.request("persona@example.test");
        var second = service.request("persona@example.test");

        assertNotEquals(first.token(), second.token());
        assertEquals(user.getCorreo(), first.email());
        ArgumentCaptor<RecuperacionContrasena> saved = ArgumentCaptor.forClass(RecuperacionContrasena.class);
        verify(tokens, org.mockito.Mockito.times(2)).save(saved.capture());
        for (RecuperacionContrasena recovery : saved.getAllValues()) {
            assertNotEquals(first.token(), recovery.getTokenHash());
            assertEquals(64, recovery.getTokenHash().length());
            assertEquals(15, java.time.Duration.between(
                    recovery.getFechaCreacion(), recovery.getFechaExpiracion()).toMinutes());
        }
    }

    @Test
    void ignoraCuentasInexistentesOInactivas() {
        when(users.findByCorreo("ausente@example.test")).thenReturn(Optional.empty());
        assertEquals(null, service.request("ausente@example.test"));
        Usuario inactive = activeUser();
        inactive.setEstado("INACTIVO");
        when(users.findByCorreo("persona@example.test")).thenReturn(Optional.of(inactive));
        assertEquals(null, service.request("persona@example.test"));
    }

    @Test
    void resetActualizaHashIncrementaVersionYConsumeTokenUnaVez() {
        Usuario user = activeUser();
        RecuperacionContrasena recovery = validToken(user);
        String rawToken = "t".repeat(43);
        when(tokens.findLockedByTokenHash(hash(rawToken))).thenReturn(Optional.of(recovery));

        service.reset(rawToken, "Nueva clave! 2026");

        assertTrue(new BCryptPasswordEncoder().matches("Nueva clave! 2026", user.getContrasenaHash()));
        assertEquals(1, user.getVersionCredenciales());
        assertTrue(recovery.getFechaUso() != null);
        verify(users).save(user);
        verify(refreshSessions).revocarUsuario(user.getIdUsuario());
        assertThrows(IllegalArgumentException.class,
                () -> service.reset(rawToken, "Otra clave! 2026"));
    }

    @Test
    void tokenExpiradoOIncorrectoNoCambiaLaContrasena() {
        Usuario user = activeUser();
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        RecuperacionContrasena expired = new RecuperacionContrasena(user, "a".repeat(64),
                now.minusMinutes(16), now.minusMinutes(1));
        when(tokens.findLockedByTokenHash(anyString())).thenReturn(Optional.of(expired));
        String oldHash = user.getContrasenaHash();
        assertThrows(IllegalArgumentException.class,
                () -> service.reset("x".repeat(43), "Nueva clave! 2026"));
        assertEquals(oldHash, user.getContrasenaHash());
        assertEquals(0, user.getVersionCredenciales());
    }

    @Test
    void validarTokenUsaHashYRechazaFormatoIncorrecto() {
        Usuario user = activeUser();
        RecuperacionContrasena recovery = validToken(user);
        String raw = "r".repeat(43);
        when(tokens.findByTokenHash(hash(raw))).thenReturn(Optional.of(recovery));
        assertTrue(service.tokenLooksValid(raw));
        assertFalse(service.tokenLooksValid("short"));
    }

    private static Usuario activeUser() {
        Usuario user = new Usuario();
        user.setCorreo("persona@example.test");
        user.setEstado("ACTIVO");
        user.setContrasenaHash(new BCryptPasswordEncoder().encode("Old password > 24 characters"));
        return user;
    }

    private static RecuperacionContrasena validToken(Usuario user) {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC());
        return new RecuperacionContrasena(user, "b".repeat(64), now, now.plusMinutes(15));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
