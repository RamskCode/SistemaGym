package com.gimnasio.sistemagym.integration.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.SocioBusquedaRepository;
import com.gimnasio.sistemagym.service.GeneradorCodigoSocio;
import com.gimnasio.sistemagym.service.BloqueoDistribuidoCicloVida;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Disposable PostgreSQL 17 schema plus full Spring/Hibernate bootstrap validation. */
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PostgreSql17SchemaAndJpaIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.11-alpine"))
            .withDatabaseName("sistemagym_it")
            .withUsername("sistemagym_it")
            .withPassword(UUID.randomUUID().toString());
    private static final String JWT_SECRET = Base64.getEncoder().encodeToString(new byte[48]);
    private static final String FRONTEND_ORIGIN = "https://frontend.example.test";
    private static final String TEST_PASSWORD = "Postgres-Integration-Only-42!";
    private static final String TEST_PASSWORD_HASH = new BCryptPasswordEncoder().encode(TEST_PASSWORD);

    static {
        POSTGRES.start();
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            connection.createStatement().execute("CREATE SCHEMA extensions");
            connection.createStatement().execute(
                    "CREATE EXTENSION unaccent WITH SCHEMA extensions");
            ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource("src/test/resources/db/postgresql/sistemagym-schema.sql"));
            connection.createStatement().executeUpdate("""
                    INSERT INTO roles (nombre, descripcion) VALUES
                    ('ADMINISTRADOR', 'Rol de integración'),
                    ('RECEPCIONISTA', 'Rol de integración'),
                    ('ENTRENADOR', 'Rol de integración'),
                    ('NUTRICIONISTA', 'Rol de integración')
                    """);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO usuarios (id_rol, nombres, apellidos, correo, contrasena_hash)
                    SELECT id_rol, 'Integracion', ?, ?, ? FROM roles WHERE nombre = ?
                    """)) {
                for (String role : List.of("ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")) {
                    statement.setString(1, role);
                    statement.setString(2, "postgres-it-" + role.toLowerCase(java.util.Locale.ROOT)
                            + "@example.test");
                    statement.setString(3, TEST_PASSWORD_HASH);
                    statement.setString(4, role);
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            POSTGRES.stop();
            throw new ExceptionInInitializerError(exception);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private SocioRepository socioRepository;
    @Autowired @Qualifier("socioBusquedaRepositoryImpl")
    private SocioBusquedaRepository socioBusquedaRepository;
    @Autowired private GeneradorCodigoSocio generadorCodigoSocio;
    @Autowired private BloqueoDistribuidoCicloVida bloqueoDistribuido;
    @Autowired private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET search_path TO pg_catalog, public");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.database.provider", () -> "postgresql");
        registry.add("app.jwt.secret-base64", () -> JWT_SECRET);
        registry.add("sistemagym.frontend-url", () -> FRONTEND_ORIGIN);
        registry.add("app.cors.allowed-origins", () -> FRONTEND_ORIGIN);
        registry.add("sistemagym.pases-diarios.esquema-v2-habilitado", () -> true);
        registry.add("sistemagym.membresias.scheduler.enabled", () -> "false");
    }

    @AfterAll
    static void disposePostgres() {
        POSTGRES.stop();
    }

    @Test
    void arrancaConElPerfilProdYValidaLas25TablasEnPostgres() {
        assertEquals("17.11", jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version')", String.class));
        assertEquals("1.1", jdbcTemplate.queryForObject("""
                SELECT e.extversion FROM pg_extension e
                JOIN pg_namespace n ON n.oid = e.extnamespace
                WHERE e.extname = 'unaccent' AND n.nspname = 'extensions'
                """, String.class));
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                  SELECT 1 FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
                  WHERE e.extname = 'unaccent' AND n.nspname = 'extensions'
                ) AND to_regprocedure('extensions.unaccent(text)') IS NOT NULL
                  AND NOT ('extensions' = ANY(current_schemas(false)))
                """, Boolean.class));
        assertTrue(jdbcTemplate.queryForObject("""
                SELECT collprovider = 'i' AND NOT collisdeterministic
                FROM pg_collation WHERE collname = 'sistemagym_ai_ci'
                """, Boolean.class), "La colación ICU debe ser no determinista en PostgreSQL 17");
        assertEquals(25, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, Integer.class));
        assertEquals(40, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace AND contype = 'f'
                """, Integer.class));
        assertEquals(112, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace AND contype = 'c'
                """, Integer.class));
    }

    @Test
    void loginRefreshLogoutCorsYPermisosDeLosCuatroRolesFuncionanEnPostgres() throws Exception {
        String adminToken = login("ADMINISTRADOR");
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        for (String role : List.of("RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")) {
            String token = login(role);
            mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        var loginResult = mockMvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND_ORIGIN)
                        .contentType("application/json")
                        .content(loginBody("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(FRONTEND_ORIGIN,
                loginResult.getResponse().getHeader("Access-Control-Allow-Origin"));
        List<String> cookies = loginResult.getResponse().getHeaders("Set-Cookie");
        Assertions.assertTrue(cookies.stream().anyMatch(value -> value.contains("SameSite=None")
                && value.contains("Secure")));
        String refresh = cookieValue(cookies, "SG_REFRESH");
        String csrf = cookieValue(cookies, "SG_CSRF");

        var refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("X-CSRF-Token", csrf)
                        .cookie(new Cookie("SG_REFRESH", refresh), new Cookie("SG_CSRF", csrf)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> rotatedCookies = refreshResult.getResponse().getHeaders("Set-Cookie");
        String rotatedRefresh = cookieValue(rotatedCookies, "SG_REFRESH");
        String rotatedCsrf = cookieValue(rotatedCookies, "SG_CSRF");
        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("X-CSRF-Token", rotatedCsrf)
                        .cookie(new Cookie("SG_REFRESH", rotatedRefresh),
                                new Cookie("SG_CSRF", rotatedCsrf)))
                .andExpect(status().isNoContent());
    }

    @Test
    void advisoryLockPostgresSerializaLaGeneracionConcurrenteDeCodigos() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            List<CompletableFuture<String>> tasks = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> transaction.execute(status ->
                            generadorCodigoSocio.crearConSiguienteCodigo(codigo -> {
                                Socio socio = new Socio();
                                socio.setCodigoSocio(codigo);
                                socio.setNombres("PG Test " + index);
                                socio.setApellidos("Concurrency");
                                socio.setEstado("ACTIVO");
                                return socioRepository.saveAndFlush(socio);
                            })).getCodigoSocio(), executor))
                    .toList();
            List<String> codes = tasks.stream().map(CompletableFuture::join).toList();
            assertEquals(4, new HashSet<>(codes).size());
            assertEquals(4L, codes.stream().filter(code -> code.matches("SOC-[0-9]{4,}")).count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void bloqueoDeCicloDeVidaPostgresEsExclusivoYSeLiberaAlFinalizar() throws Exception {
        Boolean obtenido = bloqueoDistribuido.ejecutarSiDisponible(() -> jdbcTemplate.queryForObject(
                "SELECT NOT pg_try_advisory_lock(1936728421, 2)", Boolean.class));
        assertTrue(Boolean.TRUE.equals(obtenido));
        Boolean vuelveAEstarDisponible = bloqueoDistribuido.ejecutarSiDisponible(() -> true);
        assertTrue(Boolean.TRUE.equals(vuelveAEstarDisponible));
    }

    @Test
    void reglaEstrictaDeIdempotenciaRechazaPayloadEnOperacionRechazada() {
        Long userId = jdbcTemplate.queryForObject("""
                SELECT id_usuario FROM usuarios WHERE correo = 'postgres-it-administrador@example.test'
                """, Long.class);
        jdbcTemplate.update("""
                INSERT INTO operaciones_pago_idempotentes
                    (id_operacion, id_usuario, clave_idempotencia, tipo_operacion, hash_solicitud)
                VALUES (?, ?, ?, 'MEMBRESIA_INICIAL', ?)
                """, UUID.randomUUID().toString(), userId, UUID.randomUUID().toString(), "a".repeat(64));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO operaciones_pago_idempotentes
                    (id_operacion, id_usuario, clave_idempotencia, tipo_operacion, hash_solicitud,
                     estado, resultado_payload, rechazo_tipo, rechazo_mensaje)
                VALUES (?, ?, ?, 'MEMBRESIA_INICIAL', ?, 'REJECTED', '{}', 'INVALID', 'Rechazada')
                """, UUID.randomUUID().toString(), userId, UUID.randomUUID().toString(), "b".repeat(64)));
    }

    @Test
    void colacionMySqlPreservaIgualdadCaseAccentInsensitiveYUnicidad() {
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT 'SistemaGym' COLLATE sistemagym_ai_ci = 'sistemagym' COLLATE sistemagym_ai_ci",
                Boolean.class));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT 'área' COLLATE sistemagym_ai_ci = 'AREA' COLLATE sistemagym_ai_ci",
                Boolean.class));

        jdbcTemplate.update("INSERT INTO roles (nombre) VALUES (?)", "ColaciónÁrbol");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO roles (nombre) VALUES (?)", "colacionarbol"));

        String code = "PG" + UUID.randomUUID().toString().substring(0, 10);
        jdbcTemplate.update("INSERT INTO socios (codigo_socio, nombres, apellidos) VALUES (?, ?, ?)",
                code, "ÁREA", "Entrenamiento");
        List<Socio> encontrados = socioBusquedaRepository.buscarGlobal("area entrenamiento");
        assertTrue(encontrados.stream().anyMatch(socio -> code.equals(socio.getCodigoSocio())),
                "La búsqueda global debe ignorar mayúsculas y acentos como MySQL ai_ci");
    }

    @Test
    void triggersActualizanTimestampsYRespetanTimestampExplicitoDeJpa() {
        LocalDateTime antiguo = LocalDateTime.of(2000, 1, 1, 0, 0);
        jdbcTemplate.update("INSERT INTO configuraciones (clave, valor, fecha_actualizacion) "
                        + "VALUES (?, ?, ?)", "trigger-it-" + UUID.randomUUID(), "antes",
                Timestamp.valueOf(antiguo));
        Long configId = jdbcTemplate.queryForObject(
                "SELECT max(id_configuracion) FROM configuraciones", Long.class);
        jdbcTemplate.update("UPDATE configuraciones SET valor = ? WHERE id_configuracion = ?",
                "después", configId);
        LocalDateTime configTimestamp = jdbcTemplate.queryForObject(
                "SELECT fecha_actualizacion FROM configuraciones WHERE id_configuracion = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), configId);
        assertTrue(configTimestamp.isAfter(antiguo));

        Long userId = jdbcTemplate.queryForObject("SELECT id_usuario FROM usuarios "
                + "WHERE correo = 'postgres-it-administrador@example.test'", Long.class);
        String operationId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO operaciones_pago_idempotentes "
                        + "(id_operacion, id_usuario, clave_idempotencia, tipo_operacion, hash_solicitud, "
                        + "fecha_creacion, fecha_actualizacion) VALUES (?, ?, ?, ?, ?, ?, ?)",
                operationId, userId, UUID.randomUUID().toString(), "MEMBRESIA_INICIAL",
                "a".repeat(64), Timestamp.valueOf(antiguo), Timestamp.valueOf(antiguo));
        jdbcTemplate.update("UPDATE operaciones_pago_idempotentes SET hash_solicitud = ? "
                        + "WHERE id_operacion = ?", "b".repeat(64), operationId);
        LocalDateTime operationTimestamp = jdbcTemplate.queryForObject(
                "SELECT fecha_actualizacion FROM operaciones_pago_idempotentes WHERE id_operacion = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), operationId);
        assertTrue(operationTimestamp.isAfter(antiguo));

        LocalDateTime suppliedByJpa = LocalDateTime.of(2020, 1, 1, 0, 0);
        jdbcTemplate.update("UPDATE configuraciones SET valor = ?, fecha_actualizacion = ? "
                        + "WHERE id_configuracion = ?", "callback", Timestamp.valueOf(suppliedByJpa), configId);
        assertEquals(suppliedByJpa, jdbcTemplate.queryForObject(
                "SELECT fecha_actualizacion FROM configuraciones WHERE id_configuracion = ?",
                (rs, row) -> rs.getTimestamp(1).toLocalDateTime(), configId));
    }

    @Test
    void columnasGeneradasYUnicidadDeSesionAbiertaSeConservan() {
        assertEquals(3, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND is_generated = 'ALWAYS'
                  AND (table_name, column_name) IN (
                    ('asistencias_laborales', 'id_usuario_sesion_abierta_activa'),
                    ('ingresos', 'fecha_ingreso'), ('ingresos', 'fecha_venta_pase'))
                """, Integer.class));
        Long userId = jdbcTemplate.queryForObject("SELECT id_usuario FROM usuarios "
                + "WHERE correo = 'postgres-it-administrador@example.test'", Long.class);
        jdbcTemplate.update("INSERT INTO asistencias_laborales "
                        + "(id_usuario, fecha_entrada_utc, fecha_laboral, id_registrado_entrada) "
                        + "VALUES (?, ?, CURRENT_DATE, ?)", userId,
                Timestamp.valueOf(LocalDateTime.now()), userId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO asistencias_laborales "
                        + "(id_usuario, fecha_entrada_utc, fecha_laboral, id_registrado_entrada) "
                        + "VALUES (?, ?, CURRENT_DATE, ?)", userId,
                Timestamp.valueOf(LocalDateTime.now().plusSeconds(1)), userId));

        jdbcTemplate.update("INSERT INTO contactos_promocionales (nombre, celular) VALUES (?, ?)",
                "Contacto generado", "900" + UUID.randomUUID().toString().substring(0, 7));
        Long contactId = jdbcTemplate.queryForObject(
                "SELECT max(id_contacto) FROM contactos_promocionales", Long.class);
        jdbcTemplate.update("INSERT INTO pagos (id_usuario, monto, metodo_pago) VALUES (?, ?, ?)",
                userId, java.math.BigDecimal.ONE, "EFECTIVO");
        Long paymentId = jdbcTemplate.queryForObject("SELECT max(id_pago) FROM pagos", Long.class);
        LocalDateTime fixedTime = LocalDateTime.of(2025, 3, 4, 12, 0);
        jdbcTemplate.update("INSERT INTO ingresos (id_contacto, tipo_ingreso, tipo_documento, "
                        + "numero_documento, id_usuario, id_pago, fecha_hora) "
                        + "VALUES (?, 'VISITANTE', 'DNI', ?, ?, ?, ?)",
                contactId, "48273165", userId, paymentId, Timestamp.valueOf(fixedTime));
        assertEquals(java.sql.Date.valueOf(fixedTime.toLocalDate()), jdbcTemplate.queryForObject(
                "SELECT fecha_ingreso FROM ingresos WHERE numero_documento = ?", java.sql.Date.class,
                "48273165"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO ingresos (id_contacto, tipo_ingreso, tipo_documento, numero_documento, "
                        + "id_usuario, id_pago, fecha_hora) "
                        + "VALUES (?, 'VISITANTE', 'DNI', ?, ?, ?, ?)",
                contactId, "48273165", userId, paymentId, Timestamp.valueOf(fixedTime.plusHours(1))));
    }

    @Test
    void limitesPostgresAcordadosAceptanMaximoJavaYRechazanNegativos() {
        jdbcTemplate.update("INSERT INTO roles (id_rol, nombre) VALUES (?, ?)",
                Long.MAX_VALUE, "LimiteIdJava");
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO roles (id_rol, nombre) VALUES (?, ?)",
                        -1L, "IdNegativoJava"));
        jdbcTemplate.update("INSERT INTO planes (nombre, precio, duracion_dias) VALUES (?, ?, ?)",
                "LimiteDuracionJava", java.math.BigDecimal.ONE, Integer.MAX_VALUE);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("INSERT INTO planes (nombre, precio, duracion_dias) "
                        + "VALUES (?, ?, ?)", "DuracionNoPositiva", java.math.BigDecimal.ONE, 0));
    }

    @Test
    void modulosDeSociosPagosPasesAsistenciaNutricionChecklistIncidenciasYAuditoriaRespondenEnPostgres()
            throws Exception {
        String adminToken = login("ADMINISTRADOR");
        var socioCreado = mockMvc.perform(post("/api/socios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"nombres\":\"Socio PostgreSQL Ficticio\","
                                + "\"apellidos\":\"Prueba Aislada\",\"dni\":\"48273165\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String response = socioCreado.getResponse().getContentAsString();
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern
                .compile("\"idSocio\":(\\d+)").matcher(response);
        assertTrue(idMatcher.find(), "The created socio response must contain idSocio.");
        String socioId = idMatcher.group(1);

        mockMvc.perform(get("/api/socios").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/membresias").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingresos/socios/habilitados")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/asistencias-laborales?page=0&size=20")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/checklists").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes-incidencias")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        String nutritionToken = login("NUTRICIONISTA");
        mockMvc.perform(get("/api/socios/" + socioId + "/evaluaciones-nutricionales")
                        .header("Authorization", "Bearer " + nutritionToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/nutricion/resumen").header("Authorization", "Bearer " + nutritionToken))
                .andExpect(status().isOk());

        String planName = "PG_IT_" + UUID.randomUUID().toString().substring(0, 8);
        Long planId = jdbcTemplate.queryForObject("""
                INSERT INTO planes (nombre, precio, duracion_dias, estado)
                VALUES (?, 100.00, 30, 'ACTIVO') RETURNING id_plan
                """, Long.class, planName);
        mockMvc.perform(post("/api/membresias")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("{\"idSocio\":" + socioId + ",\"idPlan\":" + planId
                                + ",\"montoPrimerPago\":50,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM membresias WHERE id_socio = ?",
                Integer.class, Long.valueOf(socioId)));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM pagos", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT count(*) FROM aplicaciones_pago_membresia",
                Integer.class));

        jdbcTemplate.update("""
                INSERT INTO configuraciones (clave, valor, descripcion)
                VALUES ('PRECIO_PASE_DIARIO', '8.00', 'Precio ficticio de integración')
                ON CONFLICT (clave) DO UPDATE SET valor = EXCLUDED.valor
                """);
        String recepcionistaToken = login("RECEPCIONISTA");
        String paseKey = UUID.randomUUID().toString();
        String paseBody = "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"51824637\","
                + "\"nombre\":\"Visitante Ficticio\",\"apellidos\":\"Prueba PostgreSQL\","
                + "\"celular\":\"900000001\",\"metodoPago\":\"EFECTIVO\","
                + "\"claveIdempotencia\":\"" + paseKey + "\"}";
        int pagosAntesPase = jdbcTemplate.queryForObject("SELECT count(*) FROM pagos", Integer.class);
        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + recepcionistaToken)
                        .contentType("application/json").content(paseBody))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/ingresos")
                        .header("Authorization", "Bearer " + recepcionistaToken)
                        .contentType("application/json").content(paseBody))
                .andExpect(status().isCreated());
        assertEquals(pagosAntesPase + 1,
                jdbcTemplate.queryForObject("SELECT count(*) FROM pagos", Integer.class));

        mockMvc.perform(patch("/api/socios/" + socioId + "/desactivar")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"motivo\":\"Prueba PostgreSQL aislada\"}"))
                .andExpect(status().isOk());

        assertTrue(jdbcTemplate.queryForObject("""
                SELECT count(*) > 0 FROM auditoria
                WHERE entidad = 'SOCIO' AND accion = 'DESACTIVAR_SOCIO'
                """, Boolean.class));
    }

    private String login(String role) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .header("Origin", FRONTEND_ORIGIN)
                        .contentType("application/json")
                        .content(loginBody(role)))
                .andExpect(status().isOk())
                .andReturn();
        java.util.regex.Matcher token = java.util.regex.Pattern
                .compile("\"token\":\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        assertTrue(token.find(), "The login response must include an access token.");
        assertNotEquals("", token.group(1));
        return token.group(1);
    }

    private String loginBody(String role) {
        return "{\"correo\":\"postgres-it-" + role.toLowerCase(java.util.Locale.ROOT)
                + "@example.test\",\"contrasena\":\"" + TEST_PASSWORD + "\"}";
    }

    private String cookieValue(List<String> headers, String name) {
        String prefix = name + "=";
        return headers.stream().filter(value -> value.startsWith(prefix)).findFirst()
                .orElseThrow(() -> new AssertionError("Cookie missing: " + name))
                .substring(prefix.length()).split(";", 2)[0];
    }
}
