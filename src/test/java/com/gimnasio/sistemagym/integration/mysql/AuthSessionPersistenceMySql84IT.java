package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Chromium auth-only E2E through Vite's proxy to an isolated random-port backend. */
@ActiveProfiles("e2e")
@Testcontainers
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class AuthSessionPersistenceMySql84IT extends MySql84IntegrationTestSupport {

    private static final String TEMPORARY_JWT_SECRET = crearSecretoJwtTemporal();
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Environment environment;

    @DynamicPropertySource
    static void configurarSecretoTemporal(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> TEMPORARY_JWT_SECRET);
    }

    @Test
    void loginRefreshDirectLinkReloadNavigationAndLogoutKeepOnlyValidSession() throws Exception {
        assertEquals("127.0.0.1", environment.getRequiredProperty("server.address"));
        int backendPort = environment.getRequiredProperty("local.server.port", Integer.class);
        assertTrue(environment.getRequiredProperty("spring.datasource.url")
                .contains("/sistemagym_test"));

        String username = "admin-session-" + UUID.randomUUID().toString().substring(0, 8)
                + "@example.test";
        String password = UUID.randomUUID() + "-AuthE2E";
        jdbc.update("INSERT INTO roles (nombre, descripcion) VALUES (?, ?)",
                "ADMINISTRADOR", "Usuario temporal aislado para prueba de autenticación");
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, "
                        + "contrasena_hash, estado) "
                        + "SELECT id_rol, ?, ?, ?, ?, 'ACTIVO' FROM roles WHERE nombre = ?",
                "Admin", "Session E2E", username, passwordEncoder.encode(password),
                "ADMINISTRADOR");

        int frontendPort = reservarPuertoLoopback();
        String frontendBase = "http://127.0.0.1:" + frontendPort;
        Process vite = iniciarViteApuntandoAlBackendDePrueba(frontendPort, backendPort);
        try {
            esperarEstado(vite, frontendBase + "/", 200);
            assertEquals(405, obtenerEstado(frontendBase + "/api/auth/login"),
                    "El proxy del frontend debe alcanzar Spring en su puerto aleatorio");
            ejecutarChromiumAuthOnly(vite, frontendBase, username, password);
        } finally {
            detenerProcesoYDescendientes(vite);
        }
        assertFalse(vite.isAlive(), "Vite se debe detener al terminar la comprobación E2E");
    }

    private static Process iniciarViteApuntandoAlBackendDePrueba(int frontendPort, int backendPort)
            throws IOException {
        Path frontend = Path.of(System.getProperty("user.dir"), "frontend").toAbsolutePath();
        Path viteCli = frontend.resolve("node_modules/vite/bin/vite.js");
        ProcessBuilder builder = new ProcessBuilder("node", viteCli.toString(),
                "--host", "127.0.0.1", "--port", Integer.toString(frontendPort), "--strictPort");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SISTEMAGYM_E2E", "1");
        builder.environment().put("SISTEMAGYM_E2E_BACKEND", "http://127.0.0.1:" + backendPort);
        return builder.start();
    }

    private static void ejecutarChromiumAuthOnly(Process vite, String frontendBase,
            String username, String password)
            throws Exception {
        Path frontend = Path.of(System.getProperty("user.dir"), "frontend").toAbsolutePath();
        Path playwrightCli = frontend.resolve("node_modules/@playwright/test/cli.js");
        ProcessBuilder builder = new ProcessBuilder("node", playwrightCli.toString(),
                "test", "--config=playwright.config.ts");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("SISTEMAGYM_E2E_FRONTEND", frontendBase);
        builder.environment().put("SISTEMAGYM_E2E_USERNAME", username);
        builder.environment().put("SISTEMAGYM_E2E_PASSWORD", password);
        builder.environment().put("SISTEMAGYM_E2E_ROLE", "ADMINISTRADOR");
        Process playwright = builder.start();
        try {
            if (!playwright.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("La prueba Chromium de sesión excedió 90 segundos.");
            }
            if (playwright.exitValue() != 0) {
                throw new IllegalStateException("La prueba Chromium de sesión falló con código "
                        + playwright.exitValue() + ".");
            }
            assertTrue(vite.isAlive(), "Vite debe permanecer activo durante la prueba Chromium");
        } finally {
            if (playwright.isAlive()) detenerProcesoYDescendientes(playwright);
        }
    }

    private static int reservarPuertoLoopback() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static void esperarEstado(Process process, String address, int expectedStatus)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IllegalStateException("Vite terminó antes de iniciar.");
            try {
                if (obtenerEstado(address) == expectedStatus) return;
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Vite no estuvo disponible antes del timeout.", lastFailure);
    }

    private static int obtenerEstado(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(address).toURL()
                .openConnection(Proxy.NO_PROXY);
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(3_000);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static String crearSecretoJwtTemporal() {
        byte[] secret = new byte[48];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }

    private static void detenerProcesoYDescendientes(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
