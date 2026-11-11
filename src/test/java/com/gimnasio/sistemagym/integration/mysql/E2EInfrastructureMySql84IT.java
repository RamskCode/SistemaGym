package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Starts only the fail-closed E2E profile, MySQL Testcontainer and loopback Spring server. */
@ActiveProfiles("e2e")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class E2EInfrastructureMySql84IT extends MySql84IntegrationTestSupport {

    private static final String TEMPORARY_JWT_SECRET = crearSecretoJwtTemporal();

    @LocalServerPort
    private int port;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void configurarSoloElSecretoTemporal(DynamicPropertyRegistry registry) {
        registry.add("app.jwt.secret-base64", () -> TEMPORARY_JWT_SECRET);
    }

    @Test
    void iniciaSpringSoloEnLoopbackConDatasourceYIdentidadDelContenedor() throws Exception {
        assertEquals("127.0.0.1", environment.getRequiredProperty("server.address"));
        assertTrue(port > 0 && port <= 65535, "Spring debe elegir un puerto dinámico válido");

        assertTrue(dataSource instanceof HikariDataSource,
                "El datasource debe ser el HikariDataSource comprobado por el guard fail-closed");
        HikariDataSource hikari = (HikariDataSource) dataSource;
        assertEquals(MYSQL.getJdbcUrl(), hikari.getJdbcUrl());
        assertEquals(MYSQL.getUsername(), hikari.getUsername());
        assertEquals(MYSQL.getPassword(), hikari.getPassword());
        assertEquals("validate", environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals(TEMPORARY_JWT_SECRET,
                environment.getRequiredProperty("app.jwt.secret-base64"));

        var identity = jdbc.queryForMap(
                "SELECT DATABASE() AS catalog, @@server_uuid AS server_uuid, "
                        + "VERSION() AS version, CURRENT_USER() AS effective_user");
        assertEquals(TEST_DATABASE, identity.get("catalog"));
        assertTrue(String.valueOf(identity.get("version")).matches("^8\\.4\\.13(?:[-+].*)?$"));
        assertTrue(String.valueOf(identity.get("effective_user")).startsWith(MYSQL.getUsername() + "@"));
        assertTrue(identity.get("server_uuid") != null
                        && !String.valueOf(identity.get("server_uuid")).isBlank(),
                "MySQL debe exponer su identidad de servidor");

        assertEquals(405, obtenerEstado(
                        "http://127.0.0.1:" + port + "/api/auth/login"),
                "Spring debe responder en loopback sin enviar credenciales ni escribir datos");

        String testUsername = "e2e-" + UUID.randomUUID().toString().substring(0, 12)
                + "@example.test";
        String testPassword = UUID.randomUUID() + "-E2E";
        jdbc.update("INSERT INTO roles (nombre, descripcion) VALUES (?, ?)",
                "RECEPCIONISTA", "Rol temporal para smoke E2E");
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, "
                        + "contrasena_hash, estado) "
                        + "SELECT id_rol, ?, ?, ?, ?, 'ACTIVO' FROM roles WHERE nombre = ?",
                "Usuario", "E2E Temporal", testUsername, passwordEncoder.encode(testPassword),
                "RECEPCIONISTA");

        jdbc.update("INSERT INTO roles (nombre, descripcion) VALUES (?, ?)",
                "ADMINISTRADOR", "Rol temporal para comprobar límites E2E");
        String adminUsername = "admin-e2e-" + UUID.randomUUID().toString().substring(0, 8)
                + "@example.test";
        String adminPassword = UUID.randomUUID() + "-E2E";
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, "
                        + "contrasena_hash, estado) "
                        + "SELECT id_rol, ?, ?, ?, ?, 'ACTIVO' FROM roles WHERE nombre = ?",
                "Admin", "E2E Temporal", adminUsername, passwordEncoder.encode(adminPassword),
                "ADMINISTRADOR");

        long idSocio = insertarSocioVigenteDePrueba("Socio", "Vigente E2E");
        insertarSocioVigenteDePrueba("Zelda", "Alfa E2E");
        verificarDirectorioYRegistroAutenticados(testUsername, testPassword,
                adminUsername, adminPassword, idSocio);

        int vitePort = puertoLoopbackDisponible();
        Process vite = iniciarVite(vitePort, port);
        try {
            String viteBase = "http://127.0.0.1:" + vitePort;
            esperarEstado(vite, viteBase + "/", 200);
            assertEquals(405, obtenerEstado(viteBase + "/api/auth/login"),
                    "Vite debe reenviar /api al backend aislado, no a localhost:8080");
            ejecutarPruebaPlaywright(vite, viteBase, testUsername, testPassword);
        } finally {
            detenerProcesoYDescendientes(vite);
        }
        assertFalse(vite.isAlive(), "Vite debe quedar detenido al finalizar incluso si falla una aserción");
    }

    private long insertarSocioVigenteDePrueba(String nombres, String apellidos) {
        String codigo = "E2E-" + UUID.randomUUID().toString().substring(0, 8);
        String dni = String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        jdbc.update("INSERT INTO socios (codigo_socio, dni, tipo_documento, numero_documento, "
                        + "nombres, apellidos, estado) VALUES (?, ?, 'DNI', ?, ?, ?, 'ACTIVO')",
                codigo, dni, dni, nombres, apellidos);
        long idSocio = jdbc.queryForObject("SELECT id_socio FROM socios WHERE codigo_socio = ?",
                Long.class, codigo);
        jdbc.update("INSERT INTO planes (nombre, precio, duracion_dias, estado) "
                        + "VALUES (?, 50.00, 30, 'ACTIVO')", "Plan E2E " + codigo);
        long idPlan = jdbc.queryForObject("SELECT id_plan FROM planes WHERE nombre = ?",
                Long.class, "Plan E2E " + codigo);
        jdbc.update("INSERT INTO membresias (id_socio, id_plan, precio_acordado, fecha_inicio, "
                        + "fecha_vencimiento, estado) VALUES (?, ?, 50.00, "
                        + "DATE_SUB(CURRENT_DATE, INTERVAL 1 DAY), "
                        + "DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY), 'ACTIVA')", idSocio, idPlan);
        return idSocio;
    }

    private void verificarDirectorioYRegistroAutenticados(String receptionist, String receptionistPassword,
            String administrator, String administratorPassword, long idSocio) throws Exception {
        String base = "http://127.0.0.1:" + port;
        String recepcionToken = iniciarSesion(base, receptionist, receptionistPassword);
        String adminToken = iniciarSesion(base, administrator, administratorPassword);

        HttpResult listado = solicitud(base + "/api/ingresos/socios/habilitados?nombre=vigente&page=0"
                + "&page=0&size=20&sort=apellidos,asc", "GET", recepcionToken, null);
        assertEquals(200, listado.status(), "RECEPCIONISTA debe listar socios habilitados con JWT real; respuesta="
                + listado.body());
        JsonNode contenido = objectMapper.readTree(listado.body()).path("contenido");
        assertTrue(contenido.isArray() && contenido.size() == 1,
                "El listado paginado debe incluir la membresía vigente: " + listado.body());
        assertEquals(idSocio, contenido.get(0).path("idSocio").asLong());

        HttpResult busqueda = solicitud(base + "/api/ingresos/socios/habilitados?nombre=Soci"
                + "&page=0&size=20&sort=nombres,desc", "GET", recepcionToken, null);
        assertEquals(200, busqueda.status());
        assertEquals(1, objectMapper.readTree(busqueda.body()).path("totalElementos").asInt());
        HttpResult ordenDescendente = solicitud(base + "/api/ingresos/socios/habilitados?page=0"
                + "&size=20&sort=nombres,desc", "GET", recepcionToken, null);
        JsonNode ordenados = objectMapper.readTree(ordenDescendente.body()).path("contenido");
        assertEquals(2, ordenados.size());
        assertEquals("Zelda", ordenados.get(0).path("nombres").asText());

        HttpResult registroAdmin = solicitud(base + "/api/ingresos/membresia", "POST", adminToken,
                Map.of("idSocio", idSocio, "claveIdempotencia", UUID.randomUUID().toString()));
        assertEquals(403, registroAdmin.status(), "ADMINISTRADOR no registra accesos físicos");
        HttpResult listadoAdmin = solicitud(base + "/api/ingresos/socios/habilitados", "GET", adminToken, null);
        assertEquals(200, listadoAdmin.status(), "ADMINISTRADOR puede consultar para supervisión");

        HttpResult registro = solicitud(base + "/api/ingresos/membresia", "POST", recepcionToken,
                Map.of("idSocio", idSocio, "claveIdempotencia", UUID.randomUUID().toString()));
        assertEquals(201, registro.status(), "RECEPCIONISTA puede registrar el ingreso autorizado");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ingresos WHERE id_socio = ?", Integer.class, idSocio));
    }

    private String iniciarSesion(String base, String username, String password) throws Exception {
        HttpResult response = solicitud(base + "/api/auth/login", "POST", null,
                Map.of("correo", username, "contrasena", password));
        assertEquals(200, response.status(), "Login real de usuario temporal");
        return objectMapper.readTree(response.body()).path("token").asText();
    }

    private HttpResult solicitud(String address, String method, String token, Object body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection(Proxy.NO_PROXY);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3_000);
        connection.setReadTimeout(5_000);
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(objectMapper.writeValueAsBytes(body));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        String responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new HttpResult(status, responseBody);
    }

    private record HttpResult(int status, String body) { }

    private static int puertoLoopbackDisponible() throws IOException {
        try (ServerSocket socket = new ServerSocket(
                0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }

    private static Process iniciarVite(int vitePort, int backendPort) throws IOException {
        Path frontend = Path.of(System.getProperty("user.dir"), "frontend").toAbsolutePath();
        Path viteCli = frontend.resolve("node_modules/vite/bin/vite.js");
        ProcessBuilder builder = new ProcessBuilder("node", viteCli.toString(),
                "--host", "127.0.0.1", "--port", String.valueOf(vitePort), "--strictPort");
        builder.directory(frontend.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SISTEMAGYM_E2E", "1");
        builder.environment().put("SISTEMAGYM_E2E_BACKEND",
                "http://127.0.0.1:" + backendPort);
        return builder.start();
    }

    private static void ejecutarPruebaPlaywright(Process vite, String frontendBase,
            String username, String password) throws Exception {
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
        Process playwright = builder.start();
        try {
            if (!playwright.waitFor(90, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("La prueba Chromium excedió 90 segundos.");
            }
            if (playwright.exitValue() != 0) {
                throw new IllegalStateException("La prueba Chromium terminó con código "
                        + playwright.exitValue() + ".");
            }
            if (!vite.isAlive()) {
                throw new IllegalStateException("Vite terminó durante la prueba Chromium.");
            }
        } finally {
            if (playwright.isAlive()) detenerProcesoYDescendientes(playwright);
        }
    }

    private static void esperarEstado(Process process,
            String url, int expectedStatus) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
        IOException lastFailure = null;
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) {
                throw new IllegalStateException("Vite terminó antes de estar disponible.");
            }
            try {
                if (obtenerEstado(url) == expectedStatus) return;
            } catch (IOException failure) {
                lastFailure = failure;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("Vite no respondió con HTTP " + expectedStatus
                + " antes del timeout.", lastFailure);
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

    private static void detenerProcesoYDescendientes(Process process) throws InterruptedException {
        List<ProcessHandle> descendants = process.toHandle().descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            descendants.stream().filter(ProcessHandle::isAlive)
                    .forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        descendants.stream().filter(ProcessHandle::isAlive)
                .forEach(ProcessHandle::destroyForcibly);
    }

    private static String crearSecretoJwtTemporal() {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        return Base64.getEncoder().encodeToString(secret);
    }
}
