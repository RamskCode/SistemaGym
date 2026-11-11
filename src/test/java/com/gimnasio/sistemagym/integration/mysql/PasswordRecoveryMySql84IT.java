package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gimnasio.sistemagym.service.PasswordRecoveryService;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordRecoveryMySql84IT extends MySql84IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordRecoveryService recovery;
    @Autowired private PasswordEncoder encoder;

    private String email;

    @BeforeEach
    void createActiveAccount() {
        jdbc.update("INSERT IGNORE INTO roles (nombre, descripcion) VALUES ('RECOVERY_TEST', 'isolated test')");
        Long roleId = jdbc.queryForObject("SELECT id_rol FROM roles WHERE nombre = 'RECOVERY_TEST'", Long.class);
        email = "recovery-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, contrasena_hash, estado) "
                        + "VALUES (?, 'Test', 'Recovery', ?, ?, 'ACTIVO')",
                roleId, email, encoder.encode("Old password longer than twenty-four chars"));
    }

    @Test
    void persistsOnlyTokenHashAndResetAtomicallyRevokesCredentialsAndConsumesToken() {
        var issued = recovery.request(email);
        String storedHash = jdbc.queryForObject(
                "SELECT token_hash FROM recuperaciones_contrasena WHERE id_usuario = "
                        + "(SELECT id_usuario FROM usuarios WHERE correo = ?)",
                String.class, email);
        assertEquals(64, storedHash.length());
        assertNotEqualsToken(issued.token(), storedHash);
        assertTrue(recovery.tokenLooksValid(issued.token()));

        recovery.reset(issued.token(), "Nueva clave! gym 2026");

        String hash = jdbc.queryForObject("SELECT contrasena_hash FROM usuarios WHERE correo = ?", String.class, email);
        Long credentialVersion = jdbc.queryForObject(
                "SELECT version_credenciales FROM usuarios WHERE correo = ?", Long.class, email);
        assertTrue(encoder.matches("Nueva clave! gym 2026", hash));
        assertFalse(encoder.matches("Old password longer than twenty-four chars", hash));
        assertEquals(1L, credentialVersion);
        assertFalse(recovery.tokenLooksValid(issued.token()));
        assertThrows(IllegalArgumentException.class,
                () -> recovery.reset(issued.token(), "Otra clave gym 2026"));
    }

    @Test
    void concurrentConsumptionAllowsExactlyOnePasswordReset() throws Exception {
        var issued = recovery.request(email);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> resetAfterBarrier(barrier, issued.token(), "Primera clave! gym 2026"));
            var second = pool.submit(() -> resetAfterBarrier(barrier, issued.token(), "Segunda clave! gym 2026"));
            boolean firstApplied = first.get(20, TimeUnit.SECONDS);
            boolean secondApplied = second.get(20, TimeUnit.SECONDS);
            assertTrue(firstApplied ^ secondApplied);
        }
    }

    private boolean resetAfterBarrier(CyclicBarrier barrier, String token, String password)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            recovery.reset(token, password);
            return true;
        } catch (IllegalArgumentException alreadyConsumed) {
            return false;
        }
    }

    private static void assertNotEqualsToken(String token, String hash) {
        assertFalse(token.equals(hash), "Solo se persiste el hash del token, nunca el token en claro");
    }
}
