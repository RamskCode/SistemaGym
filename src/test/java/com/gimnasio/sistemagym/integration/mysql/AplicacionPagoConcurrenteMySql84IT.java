package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.gimnasio.sistemagym.entity.AplicacionPagoMembresia;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.PagoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@Execution(ExecutionMode.SAME_THREAD)
class AplicacionPagoConcurrenteMySql84IT extends MySql84IntegrationTestSupport {

    private static final BigDecimal CINCUENTA = new BigDecimal("50.00");
    private static final BigDecimal SESENTA = new BigDecimal("60.00");

    @Autowired private PagoService pagoService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private PagoRepository pagoRepository;
    @Autowired private AplicacionPagoMembresiaRepository aplicacionRepository;

    @MockitoSpyBean
    private AplicacionPagoMembresiaRepository aplicacionRepositorySpy;

    private Rol rol;
    private Usuario actor;
    private Socio socio;
    private Plan plan;
    private Membresia membresia;

    @BeforeEach
    void crearActorDePrueba() {
        rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        rol = rolRepository.saveAndFlush(rol);

        actor = new Usuario();
        actor.setRol(rol);
        actor.setNombres("Actor Integracion");
        actor.setApellidos("Aplicacion Concurrente");
        actor.setCorreo("actor-" + token() + "@example.test");
        actor.setContrasenaHash("fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarSoloFixturesDeEstaPrueba() {
        if (actor != null) {
            jdbcTemplate.update("""
                    DELETE FROM aplicaciones_pago_membresia
                    WHERE id_membresia IN (
                        SELECT id_membresia FROM membresias WHERE id_socio = ?
                    ) OR id_pago IN (
                        SELECT id_pago FROM pagos WHERE id_usuario = ?
                    )
                    """, socio == null ? -1L : socio.getIdSocio(),
                    actor.getIdUsuario());
            if (socio != null) {
                jdbcTemplate.update("DELETE FROM membresias WHERE id_socio = ?",
                        socio.getIdSocio());
            }
            jdbcTemplate.update("DELETE FROM pagos WHERE id_usuario = ?",
                    actor.getIdUsuario());
        }
        if (socio != null) {
            socioRepository.deleteById(socio.getIdSocio());
        }
        if (plan != null) {
            planRepository.deleteById(plan.getIdPlan());
        }
        if (actor != null) {
            usuarioRepository.deleteById(actor.getIdUsuario());
        }
        if (rol != null) {
            rolRepository.deleteById(rol.getIdRol());
        }
    }

    @Test
    void serializaSolicitudesQueCompetirianPorElSaldoDelMismoPago() throws Exception {
        crearMembresia(new BigDecimal("200.00"));
        Pago pago = crearPago(new BigDecimal("80.00"));

        List<Boolean> resultados = ejecutarSimultaneamente(
                () -> aplicar(pago.getIdPago(), membresia.getIdMembresia(), CINCUENTA),
                () -> aplicar(pago.getIdPago(), membresia.getIdMembresia(), CINCUENTA));

        assertEquals(1L, resultados.stream().filter(Boolean::booleanValue).count(),
                "Solo una aplicación de S/ 50 debe consumir el saldo de S/ 80.");
        assertEquals(new BigDecimal("50.00"),
                aplicacionRepository.sumarAplicacionesPorPago(pago.getIdPago()));
        assertEquals(1L, contarAplicacionesPorPago(pago.getIdPago()));
        assertEquals(new BigDecimal("50.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals(new BigDecimal("80.00"), montoPago(pago.getIdPago()));
        assertEquals(1L, contarPagosDelActor());
    }

    @Test
    void serializaSolicitudesQueCompetirianPorElSaldoDeLaMembresia() throws Exception {
        crearMembresia(new BigDecimal("100.00"));
        Pago pagoA = crearPago(new BigDecimal("80.00"));
        Pago pagoB = crearPago(new BigDecimal("80.00"));

        List<Boolean> resultados = ejecutarSimultaneamente(
                () -> aplicar(pagoA.getIdPago(), membresia.getIdMembresia(), SESENTA),
                () -> aplicar(pagoB.getIdPago(), membresia.getIdMembresia(), SESENTA));

        assertEquals(1L, resultados.stream().filter(Boolean::booleanValue).count(),
                "Solo una aplicación de S/ 60 debe consumir el saldo de S/ 100.");
        assertEquals(new BigDecimal("60.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals(1L, contarAplicacionesDeLaMembresia());
        assertTrue(aplicacionRepository.sumarAplicacionesPorPago(pagoA.getIdPago())
                        .compareTo(montoPago(pagoA.getIdPago())) <= 0);
        assertTrue(aplicacionRepository.sumarAplicacionesPorPago(pagoB.getIdPago())
                        .compareTo(montoPago(pagoB.getIdPago())) <= 0);
        assertEquals(2L, contarPagosDelActor(),
                "Aplicar un pago no debe crear pagos adicionales.");
    }

    @Test
    void falloAlPersistirLaAplicacionNoDejaCambiosParciales() {
        crearMembresia(new BigDecimal("200.00"));
        Pago pago = crearPago(new BigDecimal("80.00"));
        doThrow(new DataIntegrityViolationException("Fallo de aplicacion inyectado"))
                .when(aplicacionRepositorySpy)
                .saveAndFlush(any(AplicacionPagoMembresia.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> pagoService.aplicarPagoAMembresia(
                        pago.getIdPago(), membresia.getIdMembresia(), CINCUENTA,
                        TipoAplicacionPago.INICIAL));

        assertEquals(0, aplicacionRepository.sumarAplicacionesPorPago(pago.getIdPago())
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, aplicacionRepository.sumarAplicacionesPorMembresia(
                membresia.getIdMembresia()).compareTo(BigDecimal.ZERO));
        assertEquals("PENDIENTE_PAGO", membresiaRepository
                .findById(membresia.getIdMembresia()).orElseThrow().getEstado());
        assertEquals(1L, contarPagosDelActor(),
                "El fallo no debe crear un pago adicional.");
    }

    private List<Boolean> ejecutarSimultaneamente(Callable<Boolean> primera,
            Callable<Boolean> segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier inicio = new CyclicBarrier(3);
        try {
            Future<Boolean> resultadoA = executor.submit(() -> ejecutarTrasBarrera(inicio, primera));
            Future<Boolean> resultadoB = executor.submit(() -> ejecutarTrasBarrera(inicio, segunda));
            inicio.await(20, TimeUnit.SECONDS);
            return List.of(resultadoA.get(40, TimeUnit.SECONDS),
                    resultadoB.get(40, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Los hilos de prueba deben finalizar.");
        }
    }

    private Boolean ejecutarTrasBarrera(CyclicBarrier inicio, Callable<Boolean> operacion)
            throws Exception {
        inicio.await(20, TimeUnit.SECONDS);
        return operacion.call();
    }

    private boolean aplicar(Long idPago, Long idMembresia, BigDecimal monto) {
        try {
            pagoService.aplicarPagoAMembresia(
                    idPago, idMembresia, monto, TipoAplicacionPago.INICIAL);
            return true;
        } catch (IllegalArgumentException saldoInsuficiente) {
            return false;
        }
    }

    private void crearMembresia(BigDecimal precio) {
        String token = token();
        socio = new Socio();
        socio.setCodigoSocio("IT-AP-" + token.substring(0, 10));
        socio.setNombres("Socio Integracion");
        socio.setApellidos("Aplicacion " + token.substring(0, 8));
        socio = socioRepository.saveAndFlush(socio);

        plan = new Plan();
        plan.setNombre("IT_AP_" + token.substring(0, 12));
        plan.setPrecio(precio);
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan = planRepository.saveAndFlush(plan);

        membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(precio);
        membresia.setFechaInicio(LocalDate.now());
        membresia.setFechaVencimiento(LocalDate.now().plusDays(30));
        membresia.setEstado("PENDIENTE_PAGO");
        membresia = membresiaRepository.saveAndFlush(membresia);
    }

    private Pago crearPago(BigDecimal monto) {
        return pagoService.registrarPago(
                actor.getIdUsuario(), monto, "EFECTIVO", null, null);
    }

    private long contarAplicacionesPorPago(Long idPago) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia WHERE id_pago = ?",
                Long.class, idPago);
    }

    private long contarAplicacionesDeLaMembresia() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia WHERE id_membresia = ?",
                Long.class, membresia.getIdMembresia());
    }

    private long contarPagosDelActor() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
    }

    private BigDecimal montoPago(Long idPago) {
        return jdbcTemplate.queryForObject(
                "SELECT monto FROM pagos WHERE id_pago = ?", BigDecimal.class, idPago);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
