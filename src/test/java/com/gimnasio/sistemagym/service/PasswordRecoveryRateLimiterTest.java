package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordRecoveryRateLimiterTest {
    @Test
    void limitaSolicitudesPorCorreoYPorOrigenSinGuardarElCorreoEnClave() {
        PasswordRecoveryRateLimiter limiter = new PasswordRecoveryRateLimiter();
        String origin = "192.0.2.12";
        assertTrue(limiter.allow("persona@example.test", origin));
        assertTrue(limiter.allow("persona@example.test", origin));
        assertTrue(limiter.allow("persona@example.test", origin));
        assertFalse(limiter.allow("persona@example.test", origin));
        for (int index = 0; index < 7; index++) {
            assertTrue(limiter.allow("otra" + index + "@example.test", origin));
        }
        assertFalse(limiter.allow("ultima@example.test", origin));
    }

    @Test
    void correoSeNormalizaAntesDeContabilizar() {
        PasswordRecoveryRateLimiter limiter = new PasswordRecoveryRateLimiter();
        assertTrue(limiter.allow("Persona@Example.test", "192.0.2.8"));
        assertTrue(limiter.allow(" persona@example.test ", "192.0.2.9"));
        assertTrue(limiter.allow("PERSONA@example.test", "192.0.2.10"));
        assertFalse(limiter.allow("persona@example.test", "192.0.2.11"));
    }
}
