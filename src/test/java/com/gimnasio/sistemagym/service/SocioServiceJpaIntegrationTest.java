package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Auditoria;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.AuditoriaRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Function;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-socio-code-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import(SocioService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SocioServiceJpaIntegrationTest {

    @Autowired
    private SocioService socioService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @MockitoBean
    private GeneradorCodigoSocio generadorCodigoSocio;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private AuditoriaRepository auditoriaRepositorySpy;

    private Usuario usuarioAuditor;

    @BeforeEach
    void limpiarSocios() {
        auditoriaRepository.deleteAllInBatch();
        socioRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();
        Rol rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        rol = rolRepository.saveAndFlush(rol);
        usuarioAuditor = new Usuario();
        usuarioAuditor.setRol(rol);
        usuarioAuditor.setNombres("Usuario");
        usuarioAuditor.setApellidos("Auditor");
        usuarioAuditor.setCorreo("auditor@example.invalid");
        usuarioAuditor.setContrasenaHash("hash-test-no-real");
        usuarioAuditor.setEstado("ACTIVO");
        usuarioAuditor = usuarioRepository.saveAndFlush(usuarioAuditor);
    }

    @AfterEach
    void limpiarDespuesDePrueba() {
        auditoriaRepository.deleteAllInBatch();
        socioRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();
    }

    @Test
    void colisionUnicaRevierteElSegundoRegistroYConservaElExistente() {
        when(generadorCodigoSocio.crearConSiguienteCodigo(any()))
                .thenAnswer(invocation -> {
                    Function<String, Socio> crear = invocation.getArgument(0);
                    return crear.apply("SOC-0001");
                });

        var primero = socioService.registrar(nuevoSocio("Ana"));
        assertEquals("SOC-0001", primero.codigoSocio());

        assertThrows(ConflictoException.class,
                () -> socioService.registrar(nuevoSocio("Carlos")));

        assertEquals(1, socioRepository.count());
        Socio persistido = socioRepository.findByCodigoSocio("SOC-0001")
                .orElseThrow();
        assertEquals("Ana", persistido.getNombres());
    }

    @Test
    void registroUsaReadCommittedAunqueLaTransaccionExternaUseOtroAislamiento() {
        TransactionTemplate transaccionExterna = new TransactionTemplate(transactionManager);
        transaccionExterna.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        java.util.concurrent.atomic.AtomicReference<Integer> aislamientoInterno =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(generadorCodigoSocio.crearConSiguienteCodigo(any()))
                .thenAnswer(invocation -> {
                    Function<String, Socio> crear = invocation.getArgument(0);
                    aislamientoInterno.set(
                            TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                    return crear.apply("SOC-0001");
                });

        transaccionExterna.execute(status -> {
            assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            socioService.registrar(nuevoSocio("Aislamiento"));
            assertEquals(TransactionDefinition.ISOLATION_SERIALIZABLE,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return null;
        });

        assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,
                aislamientoInterno.get());
    }

    @Test
    void desactivacionGuardaLaAuditoriaConFechaGeneradaPorLaTabla() {
        Socio socio = socioRepository.saveAndFlush(nuevoSocio("Ana"));

        var respuesta = socioService.desactivar(socio.getIdSocio(),
                "Solicitud del socio", usuarioAuditor.getCorreo());

        assertEquals("INACTIVO", respuesta.estado());
        Auditoria auditoria = auditoriaRepository.findAll().getFirst();
        assertEquals(usuarioAuditor.getIdUsuario(), auditoria.getIdUsuario());
        assertEquals("DESACTIVAR_SOCIO", auditoria.getAccion());
        assertEquals("SOCIO", auditoria.getEntidad());
        assertEquals(socio.getIdSocio().toString(), auditoria.getIdRegistro());
        assertEquals("Solicitud del socio", auditoria.getDetalle().get("motivo"));
        assertEquals("ACTIVO", auditoria.getDetalle().get("estadoAnterior"));
        assertEquals("INACTIVO", auditoria.getDetalle().get("estadoNuevo"));
        org.junit.jupiter.api.Assertions.assertNotNull(auditoria.getFechaHora());
    }

    @Test
    void errorAlGuardarAuditoriaRevierteElEstadoDelSocio() {
        Socio socio = socioRepository.saveAndFlush(nuevoSocio("Ana"));
        doThrow(new org.springframework.dao.DataIntegrityViolationException(
                "fallo de auditoría simulado"))
                .when(auditoriaRepositorySpy).saveAndFlush(any(Auditoria.class));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> socioService.desactivar(socio.getIdSocio(),
                        "Solicitud del socio", usuarioAuditor.getCorreo()));

        assertEquals("ACTIVO", socioRepository.findById(socio.getIdSocio())
                .orElseThrow().getEstado());
        assertEquals(0, auditoriaRepository.count());
    }

    @Test
    void filaBloqueadaEvitaDosTransicionesConcurrentesIncompatibles() throws Exception {
        Socio socio = socioRepository.saveAndFlush(nuevoSocio("Ana"));
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primera = executor.submit(() -> intentarDesactivar(
                    inicio, socio.getIdSocio()));
            Future<Boolean> segunda = executor.submit(() -> intentarDesactivar(
                    inicio, socio.getIdSocio()));
            inicio.countDown();

            assertEquals(1, (primera.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (segunda.get(15, TimeUnit.SECONDS) ? 1 : 0));
            assertEquals("INACTIVO", socioRepository.findById(socio.getIdSocio())
                    .orElseThrow().getEstado());
            assertEquals(1, auditoriaRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean intentarDesactivar(CountDownLatch inicio, Long id) throws Exception {
        inicio.await();
        try {
            socioService.desactivar(id, "Solicitud", usuarioAuditor.getCorreo());
            return true;
        } catch (ConflictoException ex) {
            return false;
        }
    }

    private Socio nuevoSocio(String nombres) {
        Socio socio = new Socio();
        socio.setCodigoSocio("SOC-" + nombres.hashCode());
        socio.setNombres(nombres);
        socio.setApellidos("Prueba");
        return socio;
    }
}
