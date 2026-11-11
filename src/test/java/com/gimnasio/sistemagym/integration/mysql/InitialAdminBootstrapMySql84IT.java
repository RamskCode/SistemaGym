package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gimnasio.sistemagym.config.InitialAdminProperties;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.ConfiguracionRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.InitialAdminBootstrapService;
import com.gimnasio.sistemagym.service.InitialAdminBootstrapRunner;
import com.gimnasio.sistemagym.service.InitialAdminOnboardingService;
import com.gimnasio.sistemagym.service.InstallationTokenService;
import com.gimnasio.sistemagym.service.UsuarioAdminService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class InitialAdminBootstrapMySql84IT extends MySql84IntegrationTestSupport {

    private static final String PASSWORD = "Bootstrap-Test-Password!2048";

    @Autowired
    private InitialAdminBootstrapService bootstrapService;

    @Autowired
    private UsuarioAdminService usuarioAdminService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private ConfiguracionRepository configuracionRepository;

    @Autowired
    private InstallationTokenService installationTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void limpiarSoloLaBaseAisladaDeEstaClase() {
        configuracionRepository.findByClave("ONBOARDING_ADMIN_COMPLETADO")
                .ifPresent(configuracionRepository::delete);
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();
    }

    @Test
    void creaPrimerAdministradorMedianteElRunner() throws Exception {
        Rol role = guardarRol("ADMINISTRADOR");
        InitialAdminProperties properties = properties(
                "Ada", "Prueba", "bootstrap-primero@example.test", PASSWORD);

        new InitialAdminBootstrapRunner(bootstrapService, properties, false)
                .run(new DefaultApplicationArguments(new String[0]));

        Usuario admin = usuarioRepository.findByCorreo(properties.getEmail()).orElseThrow();
        assertEquals(role.getIdRol(), admin.getRol().getIdRol());
        assertEquals("ACTIVO", admin.getEstado());
        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
    }

    @Test
    void guardaLaContrasenaUsandoBcrypt() {
        Rol role = guardarRol("ADMINISTRADOR");
        InitialAdminProperties properties = properties(
                "Ada", "Hash", "bootstrap-hash@example.test", PASSWORD);

        bootstrapService.initialize(properties);

        Usuario admin = usuarioRepository.findByCorreo(properties.getEmail()).orElseThrow();
        assertNotNull(admin.getContrasenaHash());
        assertTrue(admin.getContrasenaHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches(PASSWORD, admin.getContrasenaHash()));
        assertFalse(PASSWORD.equals(admin.getContrasenaHash()));
        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
    }

    @Test
    void segundoArranqueNoDuplicaNiModificaElAdministrador() {
        Rol role = guardarRol("ADMINISTRADOR");
        InitialAdminProperties first = properties(
                "Primero", "Admin", "bootstrap-segundo@example.test", PASSWORD);
        bootstrapService.initialize(first);
        Usuario before = usuarioRepository.findByCorreo(first.getEmail()).orElseThrow();

        bootstrapService.initialize(properties(
                "Otro", "Nombre", "otro-admin@example.test", "Different-Password!2048"));

        Usuario after = usuarioRepository.findById(before.getIdUsuario()).orElseThrow();
        assertEquals(before.getCorreo(), after.getCorreo());
        assertEquals(before.getNombres(), after.getNombres());
        assertEquals(before.getApellidos(), after.getApellidos());
        assertEquals(before.getContrasenaHash(), after.getContrasenaHash());
        assertEquals(before.getEstado(), after.getEstado());
        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
    }

    @Test
    void administradorExistenteInactivoNoCreaNiModificaOtro() {
        Rol role = guardarRol("ADMINISTRADOR");
        Usuario inactive = guardarUsuario(role, "inactivo-admin@example.test", "INACTIVO");
        String originalHash = inactive.getContrasenaHash();

        bootstrapService.initialize(properties(
                "Ignorado", "Ignorado", "no-debe-crearse@example.test", PASSWORD));

        Usuario persisted = usuarioRepository.findById(inactive.getIdUsuario()).orElseThrow();
        assertEquals("INACTIVO", persisted.getEstado());
        assertEquals(originalHash, persisted.getContrasenaHash());
        assertEquals("inactivo-admin@example.test", persisted.getCorreo());
        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
    }

    @Test
    void rolAdministradorAusenteFallaSinCrearRolesNiUsuarios() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> bootstrapService.initialize(properties(
                        "Ada", "Prueba", "sin-rol@example.test", PASSWORD)));

        assertTrue(error.getMessage().contains("ADMINISTRADOR"));
        assertEquals(0, rolRepository.count());
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void variablesAusentesOIncompletasFallaSinEscrituras() {
        Rol role = guardarRol("ADMINISTRADOR");

        IllegalStateException absent = assertThrows(IllegalStateException.class,
                () -> bootstrapService.initialize(properties("", "", "", "")));
        assertTrue(absent.getMessage().contains("INITIAL_ADMIN_NAME"));

        IllegalStateException incomplete = assertThrows(IllegalStateException.class,
                () -> bootstrapService.initialize(properties("Ada", "", "", PASSWORD)));
        assertTrue(incomplete.getMessage().contains("INITIAL_ADMIN_LAST_NAME"));
        assertEquals(0, usuarioRepository.countByRoleId(role.getIdRol()));
        assertEquals(1, rolRepository.count());
    }

    @Test
    void correoOcupadoPorOtraCuentaFallaSinModificarla() {
        Rol adminRole = guardarRol("ADMINISTRADOR");
        Rol receptionistRole = guardarRol("RECEPCIONISTA");
        Usuario existing = guardarUsuario(
                receptionistRole, "correo-ocupado@example.test", "INACTIVO");
        String originalHash = existing.getContrasenaHash();

        assertThrows(ConflictoException.class, () -> bootstrapService.initialize(properties(
                "Ada", "Prueba", existing.getCorreo(), PASSWORD)));

        Usuario persisted = usuarioRepository.findById(existing.getIdUsuario()).orElseThrow();
        assertEquals(receptionistRole.getIdRol(), persisted.getRol().getIdRol());
        assertEquals("INACTIVO", persisted.getEstado());
        assertEquals(originalHash, persisted.getContrasenaHash());
        assertEquals(0, usuarioRepository.countByRoleId(adminRole.getIdRol()));
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void dosInicializadoresConcurrentesCreanExactamenteUnAdministrador() throws Exception {
        Rol role = guardarRol("ADMINISTRADOR");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> attempts = List.of(
                    executor.submit(() -> initializeConBarrera(ready, start,
                            properties("Uno", "Admin", "race-one@example.test", PASSWORD))),
                    executor.submit(() -> initializeConBarrera(ready, start,
                            properties("Dos", "Admin", "race-two@example.test", PASSWORD))));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Las dos tareas deben estar listas.");
            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get(30, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
        assertEquals(1, usuarioRepository.count());
    }

    @Test
    void onboardingSoloAceptaTokenValidoYGuardaConBcrypt() {
        Rol role = guardarRol("ADMINISTRADOR");
        InitialAdminOnboardingService onboarding = onboarding(true);
        onboarding.initializeAtStartup();
        String token = installationTokenService.generateAndPrint();
        CrearUsuarioRequest request = new CrearUsuarioRequest(
                "Ada", "Onboarding", "onboarding-ok@example.test", PASSWORD, "ADMINISTRADOR");

        assertFalse(onboarding.createFirstAdministrator("token-incorrecto", new com.gimnasio.sistemagym.dto.InitialAdminRequest(
                request.nombres(), request.apellidos(), request.correo(), request.contrasena())));
        assertEquals(0, usuarioRepository.countByRoleId(role.getIdRol()));
        assertTrue(onboarding.createFirstAdministrator(token, new com.gimnasio.sistemagym.dto.InitialAdminRequest(
                request.nombres(), request.apellidos(), request.correo(), request.contrasena())));

        Usuario admin = usuarioRepository.findByCorreo(request.correo()).orElseThrow();
        assertTrue(passwordEncoder.matches(PASSWORD, admin.getContrasenaHash()));
        assertTrue(configuracionRepository.findByClave("ONBOARDING_ADMIN_COMPLETADO").isPresent());
        assertFalse(onboarding.isRequired());
        assertFalse(installationTokenService.matches(token));
    }

    @Test
    void elMarcadorCierraOnboardingAunqueLuegoSeElimineElAdministrador() {
        guardarRol("ADMINISTRADOR");
        InitialAdminOnboardingService onboarding = onboarding(true);
        onboarding.initializeAtStartup();
        String token = installationTokenService.generateAndPrint();
        assertTrue(onboarding.createFirstAdministrator(token,
                new com.gimnasio.sistemagym.dto.InitialAdminRequest(
                        "Ada", "Persistencia", "onboarding-marker@example.test", PASSWORD)));

        usuarioRepository.deleteAllInBatch();
        InitialAdminOnboardingService restarted = onboarding(true);
        restarted.initializeAtStartup();

        assertEquals(0, usuarioRepository.count());
        assertTrue(configuracionRepository.findByClave("ONBOARDING_ADMIN_COMPLETADO").isPresent());
        assertFalse(restarted.isRequired());
    }

    @Test
    void dosSolicitudesDeOnboardingSimultaneasCreanSoloUnAdministrador() throws Exception {
        Rol role = guardarRol("ADMINISTRADOR");
        InitialAdminOnboardingService onboarding = onboarding(true);
        onboarding.initializeAtStartup();
        String token = installationTokenService.generateAndPrint();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = List.of(
                    executor.submit(() -> onboardingConBarrera(onboarding, token, ready, start,
                            "race-onboard-a@example.test")),
                    executor.submit(() -> onboardingConBarrera(onboarding, token, ready, start,
                            "race-onboard-b@example.test")));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int succeeded = 0;
            for (Future<Boolean> attempt : attempts) {
                try {
                    if (attempt.get(30, TimeUnit.SECONDS)) succeeded++;
                } catch (java.util.concurrent.ExecutionException rejectedAfterRace) {
                    assertInstanceOf(com.gimnasio.sistemagym.exception.ConflictoException.class,
                            rejectedAfterRace.getCause());
                }
            }
            assertEquals(1, succeeded);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertEquals(1, usuarioRepository.countByRoleId(role.getIdRol()));
        assertTrue(configuracionRepository.findByClave("ONBOARDING_ADMIN_COMPLETADO").isPresent());
    }

    private InitialAdminOnboardingService onboarding(boolean enabled) {
        return new InitialAdminOnboardingService(
                bootstrapService, installationTokenService, enabled);
    }

    private boolean onboardingConBarrera(InitialAdminOnboardingService onboarding,
            String token, CountDownLatch ready, CountDownLatch start, String email) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrera no liberada");
        return onboarding.createFirstAdministrator(token,
                new com.gimnasio.sistemagym.dto.InitialAdminRequest(
                        "Carrera", "Admin", email, PASSWORD));
    }

    @Test
    void rollbackRevierteLaCreacionSiFallaLaTransaccionExterna() {
        Rol role = guardarRol("ADMINISTRADOR");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(FalloForzado.class, () -> transaction.executeWithoutResult(status -> {
            bootstrapService.initialize(properties(
                    "Ada", "Rollback", "rollback-admin@example.test", PASSWORD));
            throw new FalloForzado();
        }));

        assertEquals(0, usuarioRepository.countByRoleId(role.getIdRol()));
        assertEquals(0, usuarioRepository.count());
    }

    @Test
    void mensajesYLogsNoExponenCredenciales() {
        guardarRol("ADMINISTRADOR");
        String secretMarker = "PRIVATE-BOOTSTRAP-MARKER-Password!2048";
        InitialAdminProperties incomplete = properties(
                secretMarker, "", "", secretMarker);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> bootstrapService.initialize(incomplete));
            assertFalse(error.getMessage().contains(secretMarker));
            assertTrue(appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(secretMarker)));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
        assertEquals(0, usuarioRepository.count());
    }

    private void initializeConBarrera(
            CountDownLatch ready,
            CountDownLatch start,
            InitialAdminProperties properties) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("No se liberó la barrera concurrente.");
            }
            bootstrapService.initialize(properties);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Inicialización concurrente interrumpida.");
        }
    }

    private Rol guardarRol(String name) {
        Rol role = new Rol();
        role.setNombre(name);
        return rolRepository.saveAndFlush(role);
    }

    private Usuario guardarUsuario(Rol role, String email, String state) {
        Usuario user = new Usuario();
        user.setRol(role);
        user.setNombres("Usuario");
        user.setApellidos("Fixture");
        user.setCorreo(email);
        user.setContrasenaHash(passwordEncoder.encode(PASSWORD));
        user.setEstado(state);
        return usuarioRepository.saveAndFlush(user);
    }

    private InitialAdminProperties properties(
            String name, String lastName, String email, String password) {
        return new InitialAdminProperties(name, lastName, email, password);
    }

    private static final class FalloForzado extends RuntimeException {
    }
}
