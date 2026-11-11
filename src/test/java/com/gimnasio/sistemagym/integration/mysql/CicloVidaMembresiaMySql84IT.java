package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.gimnasio.sistemagym.dto.RegistrarPagoPosteriorRequest;
import com.gimnasio.sistemagym.dto.RegistrarMembresiaRequest;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.MembresiaCicloVidaService;
import com.gimnasio.sistemagym.service.MembresiaService;
import com.gimnasio.sistemagym.service.PagoService;
import com.gimnasio.sistemagym.service.BloqueoDistribuidoCicloVida;
import com.gimnasio.sistemagym.service.DisponibilidadCicloVida;
import com.gimnasio.sistemagym.service.TareaMembresiasProgramada;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * Ciclo de vida de membresías: plazo de cinco días de servicio, cancelación
 * automática por falta de pago, idempotencia, concurrencia y vencimiento
 * natural. Las fechas se controlan mediante un Clock sustituido en pruebas.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CicloVidaMembresiaMySql84IT extends MySql84IntegrationTestSupport {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final BigDecimal PRECIO = new BigDecimal("200.00");
    private static final BigDecimal INICIAL = new BigDecimal("50.00");
    private static final BigDecimal SALDO = new BigDecimal("150.00");

    // 2026-03-07 es sábado.
    private static final LocalDate SABADO = LocalDate.of(2026, 3, 7);
    private static final LocalDate DOMINGO = SABADO.plusDays(1);
    private static final LocalDate LUNES = SABADO.plusDays(2);
    private static final LocalDate JUEVES = SABADO.plusDays(5);
    private static final LocalDate VIERNES = SABADO.plusDays(6);
    private static final LocalDate SABADO_SIGUIENTE = SABADO.plusDays(7);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private AplicacionPagoMembresiaRepository aplicacionRepository;
    @Autowired private PagoService pagoService;
    @Autowired private MembresiaService membresiaService;
    @Autowired private MembresiaCicloVidaService cicloVidaService;
    @Autowired private BloqueoDistribuidoCicloVida bloqueoDistribuido;

    @MockitoBean
    private Clock reloj;

    @MockitoBean(name = "relojMembresias")
    private Clock relojMembresias;

    @MockitoSpyBean
    private MembresiaRepository membresiaRepositorySpy;

    private Rol rol;
    private Usuario actor;
    private Socio socio;
    private Plan plan;
    private final List<Long> idsMembresias = new ArrayList<>();

    @BeforeEach
    void prepararEntorno() {
        fijarFecha(LUNES);

        rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        rol = rolRepository.saveAndFlush(rol);

        actor = new Usuario();
        actor.setRol(rol);
        actor.setNombres("Actor Integracion");
        actor.setApellidos("Ciclo Vida");
        actor.setCorreo("actor-" + token() + "@example.test");
        actor.setContrasenaHash("fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarFixturesPropiosEnOrdenDeClavesForaneas() {
        if (actor != null) {
            jdbcTemplate.update(
                    "DELETE FROM ingresos WHERE id_usuario = ?",
                    actor.getIdUsuario());
        }
        if (socio != null) {
            jdbcTemplate.update(
                    "DELETE FROM ingresos WHERE id_socio = ?",
                    socio.getIdSocio());
            jdbcTemplate.update(
                    "DELETE FROM notificaciones WHERE id_socio = ?",
                    socio.getIdSocio());
        }

        for (Long idMembresia : idsMembresias) {
            jdbcTemplate.update(
                    "DELETE FROM notificaciones WHERE id_membresia = ?",
                    idMembresia);
            jdbcTemplate.update(
                    "DELETE FROM aplicaciones_pago_membresia "
                            + "WHERE id_membresia = ?",
                    idMembresia);
        }
        for (Long idMembresia : idsMembresias) {
            jdbcTemplate.update(
                    "DELETE FROM membresias WHERE id_membresia = ?",
                    idMembresia);
        }

        if (actor != null) {
            jdbcTemplate.update("""
                    DELETE FROM aplicaciones_pago_membresia
                    WHERE id_pago IN (
                        SELECT id_pago FROM pagos WHERE id_usuario = ?
                    )
                    """, actor.getIdUsuario());
            jdbcTemplate.update(
                    "DELETE FROM pagos WHERE id_usuario = ?",
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

        idsMembresias.clear();
    }

    @Test
    void pagoCompletoDentroDelPlazoMantieneLaMembresiaActiva() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        assertEquals(VIERNES, membresia.getFechaLimitePago(),
                "El lunes es el día 1 y el viernes el día 5 de servicio.");

        fijarInstante(JUEVES.atTime(18, 0));
        pagarSaldo(membresia, SALDO);

        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));
        assertEquals("ACTIVA", estado(membresia));

        fijarInstante(SABADO_SIGUIENTE.atTime(9, 0));
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                cicloVidaService.procesarCicloVida();

        assertEquals(0, resultado.canceladas());
        assertEquals(0, resultado.vencidas());
        assertEquals("ACTIVA", estado(membresia));
        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));
    }

    @Test
    void cancelaAlFinalizarElQuintoDiaSiQuedaSaldoPendiente() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        assertEquals(VIERNES, membresia.getFechaLimitePago());

        fijarInstante(VIERNES.atTime(23, 59, 59));
        assertEquals(0, cicloVidaService
                        .cancelarMembresiasConPagoVencido(VIERNES),
                "Durante el quinto día todavía puede pagar.");
        assertEquals("ACTIVA", estado(membresia));

        fijarInstante(SABADO_SIGUIENTE.atTime(0, 1));
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                cicloVidaService.procesarCicloVida();

        assertEquals(1, resultado.canceladas());
        assertEquals("CANCELADA", estado(membresia));
        assertNotNull(motivo(membresia));
        assertTrue(motivo(membresia).toLowerCase().contains("plazo"));
        assertEquals(0, saldoPendiente(membresia).compareTo(SALDO),
                "El dinero recibido se conserva.");
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
    }

    @Test
    void domingoNoCuentaComoDiaDeServicio() {
        Membresia membresia = crearMembresiaConPagoInicial(SABADO, INICIAL);

        assertEquals(JUEVES, membresia.getFechaLimitePago(),
                "Sábado(1), lunes(2), martes(3), miércoles(4), jueves(5).");

        fijarInstante(JUEVES.atTime(23, 59, 59));
        assertEquals(0, cicloVidaService
                .cancelarMembresiasConPagoVencido(JUEVES));

        fijarInstante(VIERNES.atTime(0, 1));
        assertEquals(1, cicloVidaService
                .cancelarMembresiasConPagoVencido(VIERNES));
        assertEquals("CANCELADA", estado(membresia));
    }

    @Test
    void pagoInicialRealizadoUnSabadoCuentaEseSabadoComoDiaUno() {
        Membresia membresia = crearMembresiaConPagoInicial(SABADO, INICIAL);

        assertEquals(SABADO, membresia.getFechaInicio());
        assertEquals(JUEVES, membresia.getFechaLimitePago());
        assertEquals("ACTIVA", estado(membresia));
        assertNull(motivo(membresia));
    }

    @Test
    void pagoInicialRealizadoUnDomingoComienzaElConteoElLunes() {
        Membresia membresia = crearMembresiaConPagoInicial(DOMINGO, INICIAL);

        assertEquals(VIERNES, membresia.getFechaLimitePago(),
                "El domingo no cuenta; lunes(1) a viernes(5).");

        fijarInstante(VIERNES.atTime(23, 59, 59));
        assertEquals(0, cicloVidaService
                .cancelarMembresiasConPagoVencido(VIERNES));

        fijarInstante(SABADO_SIGUIENTE.atTime(0, 1));
        assertEquals(1, cicloVidaService
                .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));
        assertEquals("CANCELADA", estado(membresia));
    }

    @Test
    void registroRealConPagoInicialUnSabadoUsaElRelojControlado() {
        fijarFecha(SABADO);
        prepararSocioYPlan();

        var respuesta = membresiaService.registrar(
                solicitudPrimerPago(), actor.getCorreo());
        idsMembresias.add(respuesta.idMembresia());

        assertEquals(SABADO, respuesta.fechaInicio());
        assertEquals(JUEVES, respuesta.fechaLimitePago());
        assertEquals(SABADO, recargarPorId(respuesta.idMembresia()).getFechaInicio());
        assertEquals(JUEVES,
                recargarPorId(respuesta.idMembresia()).getFechaLimitePago());
    }

    @Test
    void registroRealConPagoInicialUnDomingoUsaElRelojControlado() {
        fijarFecha(DOMINGO);
        prepararSocioYPlan();

        var respuesta = membresiaService.registrar(
                solicitudPrimerPago(), actor.getCorreo());
        idsMembresias.add(respuesta.idMembresia());

        assertEquals(DOMINGO, respuesta.fechaInicio());
        assertEquals(VIERNES, respuesta.fechaLimitePago());
        assertEquals(DOMINGO, recargarPorId(respuesta.idMembresia()).getFechaInicio());
        assertEquals(VIERNES,
                recargarPorId(respuesta.idMembresia()).getFechaLimitePago());
    }

    @Test
    void pagoJustoAntesDeFinalizarElPlazoEvitaLaCancelacion() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        assertEquals(VIERNES, membresia.getFechaLimitePago());

        fijarInstante(VIERNES.atTime(23, 59, 59));
        pagarSaldo(membresia, SALDO);

        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));

        fijarInstante(SABADO.atTime(0, 1));
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                cicloVidaService.procesarCicloVida();

        assertEquals(0, resultado.canceladas());
        assertEquals("ACTIVA", estado(membresia));
        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));
    }

    @Test
    void membresiaCompletamentePagadaNoSeCancelaPorElPlazo() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, PRECIO);
        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));

        fijarInstante(SABADO.atTime(12, 0));
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                cicloVidaService.procesarCicloVida();

        assertEquals(0, resultado.canceladas());
        assertEquals("ACTIVA", estado(membresia));
        assertNull(motivo(membresia));
    }

    @Test
    void cancelacionEsIdempotente() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        fijarInstante(SABADO_SIGUIENTE.atTime(8, 0));

        assertEquals(1, cicloVidaService
                .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));
        String motivoInicial = motivo(membresia);

        assertEquals(0, cicloVidaService
                .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));
        assertEquals(0, cicloVidaService
                .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));

        assertEquals("CANCELADA", estado(membresia));
        assertEquals(motivoInicial, motivo(membresia));
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
    }

    @Test
    void ejecucionConcurrenteDelProcesoNoCancelaDosVeces() throws Exception {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        fijarInstante(SABADO_SIGUIENTE.atTime(8, 0));

        List<Integer> resultados = ejecutarSimultaneamente(
                () -> cicloVidaService
                        .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE),
                () -> cicloVidaService
                        .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));

        assertEquals(1, resultados.get(0) + resultados.get(1),
                "Solo una ejecución debe cancelar la membresía.");
        assertEquals("CANCELADA", estado(membresia));
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
    }

    @Test
    void membresiaPagadaSeMarcaVencidaAlTerminarSuVigencia() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, PRECIO);
        LocalDate despuesDelVencimiento =
                membresia.getFechaVencimiento().plusDays(1);

        fijarInstante(despuesDelVencimiento.atTime(9, 0));
        MembresiaCicloVidaService.ResultadoCicloVida resultado =
                cicloVidaService.procesarCicloVida();

        assertEquals(0, resultado.canceladas(),
                "Una membresía pagada no se cancela por deuda.");
        assertEquals(1, resultado.vencidas());
        assertEquals("VENCIDA", estado(membresia));
        assertNull(motivo(membresia));
        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));
    }

    @Test
    void membresiaPagadaPermaneceActivaDuranteSuUltimoDiaDeVigencia() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, PRECIO);
        LocalDate ultimoDia = membresia.getFechaVencimiento();

        fijarFecha(ultimoDia);
        MembresiaCicloVidaService.ResultadoCicloVida mismoDia =
                cicloVidaService.procesarCicloVida();

        assertEquals(0, mismoDia.vencidas());
        assertEquals("ACTIVA", estado(membresia));

        fijarFecha(ultimoDia.plusDays(1));
        MembresiaCicloVidaService.ResultadoCicloVida diaSiguiente =
                cicloVidaService.procesarCicloVida();
        assertEquals(1, diaSiguiente.vencidas());
        assertEquals("VENCIDA", estado(membresia));
    }

    @Test
    void revisionBloqueanteAlArrancarRecuperaCicloAntesDeHabilitarOperaciones() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, PRECIO);
        fijarFecha(membresia.getFechaVencimiento().plusDays(1));
        DisponibilidadCicloVida disponibilidad = new DisponibilidadCicloVida(true);
        TareaMembresiasProgramada tarea =
                new TareaMembresiasProgramada(cicloVidaService, bloqueoDistribuido, disponibilidad);

        assertTrue(!disponibilidad.estaDisponible(),
                "La API permanece cerrada mientras no termina la recuperación.");
        tarea.run(new DefaultApplicationArguments(new String[0]));
        assertTrue(disponibilidad.estaDisponible(),
                "Solo se habilita después de una revisión exitosa.");
        tarea.run(new DefaultApplicationArguments(new String[0]));

        assertEquals("VENCIDA", estado(membresia));
        assertEquals(0, saldoPendiente(membresia).compareTo(BigDecimal.ZERO));
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
    }

    @Test
    void bloqueoDistribuidoRechazaUnaSegundaEjecucionSimultanea() throws Exception {
        CountDownLatch primeraEnBloque = new CountDownLatch(1);
        CountDownLatch liberarPrimera = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> primera = executor.submit(() -> bloqueoDistribuido
                    .ejecutarSiDisponible(() -> {
                        primeraEnBloque.countDown();
                        if (!liberarPrimera.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timeout de prueba del bloqueo.");
                        }
                        return "ejecutada";
                    }));

            assertTrue(primeraEnBloque.await(10, TimeUnit.SECONDS));
            String segunda = bloqueoDistribuido.ejecutarSiDisponible(() -> "duplicada");
            assertNull(segunda, "El bloqueo MySQL debe impedir la ejecución concurrente.");

            liberarPrimera.countDown();
            assertEquals("ejecutada", primera.get(10, TimeUnit.SECONDS));
        } finally {
            liberarPrimera.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cancelacionNoDuplicaPagosNiAplicaciones() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        fijarInstante(SABADO_SIGUIENTE.atTime(8, 0));

        cicloVidaService.cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE);
        cicloVidaService.cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE);

        assertThrows(ConflictoException.class, () ->
                membresiaService.registrarPagoPosterior(
                        membresia.getIdMembresia(),
                        new RegistrarPagoPosteriorRequest(
                                new BigDecimal("10.00"), "EFECTIVO", null, null),
                        actor.getCorreo()));

        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
        assertEquals(0, saldoPendiente(membresia).compareTo(SALDO));
    }

    @Test
    void falloDuranteLaCancelacionRevierteElCambio() {
        Membresia membresia = crearMembresiaConPagoInicial(LUNES, INICIAL);
        fijarInstante(SABADO_SIGUIENTE.atTime(8, 0));

        doThrow(new DataIntegrityViolationException(
                "Fallo de cancelacion inyectado"))
                .when(membresiaRepositorySpy)
                .saveAndFlush(any(Membresia.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> cicloVidaService
                        .cancelarMembresiasConPagoVencido(SABADO_SIGUIENTE));

        assertEquals("ACTIVA", estado(membresia));
        assertNull(motivo(membresia));
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarAplicaciones(membresia));
    }

    private Membresia crearMembresiaConPagoInicial(
            LocalDate fechaPagoInicial, BigDecimal montoInicial) {

        fijarFecha(fechaPagoInicial);
        prepararSocioYPlan();

        Membresia membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(PRECIO);
        membresia.setFechaInicio(fechaPagoInicial);
        membresia.setFechaVencimiento(fechaPagoInicial.plusDays(30));
        membresia.setEstado("PENDIENTE_PAGO");
        membresia = membresiaRepository.saveAndFlush(membresia);
        idsMembresias.add(membresia.getIdMembresia());

        Pago pago = pagoService.registrarPago(
                actor.getIdUsuario(), montoInicial, "EFECTIVO", null, null);
        pagoService.aplicarPagoAMembresia(
                pago.getIdPago(), membresia.getIdMembresia(), montoInicial,
                TipoAplicacionPago.INICIAL);

        return recargar(membresia);
    }

    private void prepararSocioYPlan() {
        if (socio != null) {
            return;
        }

        socio = new Socio();
        socio.setCodigoSocio("IT-CV-" + tokenCorto());
        socio.setNombres("Socio Integracion");
        socio.setApellidos("Ciclo Vida " + tokenCorto());
        socio.setEstado("ACTIVO");
        socio = socioRepository.saveAndFlush(socio);

        plan = new Plan();
        plan.setNombre("IT_CV_" + tokenCorto());
        plan.setPrecio(PRECIO);
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan = planRepository.saveAndFlush(plan);
    }

    private RegistrarMembresiaRequest solicitudPrimerPago() {
        RegistrarMembresiaRequest solicitud = new RegistrarMembresiaRequest();
        solicitud.setIdSocio(socio.getIdSocio());
        solicitud.setIdPlan(plan.getIdPlan());
        solicitud.setMontoPrimerPago(INICIAL);
        solicitud.setMetodoPago("EFECTIVO");
        return solicitud;
    }

    private Membresia recargarPorId(Long idMembresia) {
        return membresiaRepository.findById(idMembresia).orElseThrow();
    }

    private void pagarSaldo(Membresia membresia, BigDecimal monto) {
        membresiaService.registrarPagoPosterior(
                membresia.getIdMembresia(),
                new RegistrarPagoPosteriorRequest(
                        monto, "EFECTIVO", null, null),
                actor.getCorreo());
    }

    private Membresia recargar(Membresia membresia) {
        return membresiaRepository.findById(membresia.getIdMembresia())
                .orElseThrow();
    }

    private String estado(Membresia membresia) {
        return recargar(membresia).getEstado();
    }

    private String motivo(Membresia membresia) {
        return recargar(membresia).getMotivoCancelacion();
    }

    private BigDecimal saldoPendiente(Membresia membresia) {
        BigDecimal totalAplicado = aplicacionRepository
                .sumarAplicacionesPorMembresia(membresia.getIdMembresia());
        return recargar(membresia).getPrecioAcordado()
                .subtract(totalAplicado);
    }

    private long contarPagos() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?",
                Long.class, actor.getIdUsuario());
    }

    private long contarAplicaciones(Membresia membresia) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ?",
                Long.class, membresia.getIdMembresia());
    }

    private void fijarFecha(LocalDate fecha) {
        fijarInstante(fecha.atTime(12, 0));
    }

    private void fijarInstante(LocalDateTime fechaHora) {
        when(reloj.getZone()).thenReturn(ZONA);
        when(reloj.instant()).thenReturn(
                fechaHora.atZone(ZONA).toInstant());
        when(relojMembresias.getZone()).thenReturn(ZONA);
        when(relojMembresias.instant()).thenReturn(
                fechaHora.atZone(ZONA).toInstant());
    }

    private List<Integer> ejecutarSimultaneamente(
            Callable<Integer> primera, Callable<Integer> segunda)
            throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier inicio = new CyclicBarrier(3);
        try {
            Future<Integer> resultadoA = executor.submit(
                    () -> ejecutarTrasBarrera(inicio, primera));
            Future<Integer> resultadoB = executor.submit(
                    () -> ejecutarTrasBarrera(inicio, segunda));
            inicio.await(20, TimeUnit.SECONDS);
            return List.of(resultadoA.get(40, TimeUnit.SECONDS),
                    resultadoB.get(40, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Los hilos de prueba deben finalizar.");
        }
    }

    private Integer ejecutarTrasBarrera(CyclicBarrier inicio,
            Callable<Integer> operacion) throws Exception {
        inicio.await(20, TimeUnit.SECONDS);
        return operacion.call();
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String tokenCorto() {
        return token().substring(0, 10);
    }
}
