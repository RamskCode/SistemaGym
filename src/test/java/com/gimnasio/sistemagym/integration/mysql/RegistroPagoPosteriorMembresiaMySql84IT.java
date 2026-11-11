package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.dto.RegistrarPagoPosteriorRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.gimnasio.sistemagym.service.MembresiaService;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class RegistroPagoPosteriorMembresiaMySql84IT extends MySql84IntegrationTestSupport {

    private static final BigDecimal DOSCIENTOS = new BigDecimal("200.00");
    private static final BigDecimal CINCUENTA = new BigDecimal("50.00");
    private static final BigDecimal CIEN = new BigDecimal("100.00");
    private static final BigDecimal SESENTA = new BigDecimal("60.00");

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private AplicacionPagoMembresiaRepository aplicacionRepository;
    @Autowired private MembresiaService membresiaService;

    @MockitoSpyBean
    private PagoService pagoService;

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
        actor.setApellidos("Pago Posterior");
        actor.setCorreo("actor-" + token() + "@example.test");
        actor.setContrasenaHash("fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarSoloFixturesDeEstaPrueba() {
        long idSocio = socio == null ? -1L : socio.getIdSocio();
        long idActor = actor == null ? -1L : actor.getIdUsuario();

        if (actor != null) {
            jdbcTemplate.update("DELETE FROM operaciones_pago_idempotentes WHERE id_usuario = ?",
                    idActor);
        }

        jdbcTemplate.update("""
                DELETE FROM aplicaciones_pago_membresia
                WHERE id_membresia IN (
                    SELECT id_membresia FROM membresias WHERE id_socio = ?
                ) OR id_pago IN (
                    SELECT id_pago FROM pagos WHERE id_usuario = ?
                )
                """, idSocio, idActor);
        if (socio != null) {
            jdbcTemplate.update("DELETE FROM membresias WHERE id_socio = ?", idSocio);
        }
        if (actor != null) {
            jdbcTemplate.update("DELETE FROM pagos WHERE id_usuario = ?", idActor);
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
    void registraPagoParcialPosteriorConResponsableDeLaAutenticacion() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(CINCUENTA, "ADMINISTRADOR", "ref-posterior", true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPago").isNumber())
                .andExpect(jsonPath("$.idAplicacion").isNumber())
                .andExpect(jsonPath("$.idMembresia")
                        .value(membresia.getIdMembresia().intValue()))
                .andExpect(jsonPath("$.montoPago").value(50.00))
                .andExpect(jsonPath("$.montoAplicado").value(50.00))
                .andExpect(jsonPath("$.metodoPago").value("YAPE"))
                .andExpect(jsonPath("$.referencia").value("ref-posterior"))
                .andExpect(jsonPath("$.observaciones").value("obs-posterior"))
                .andExpect(jsonPath("$.fechaPago").exists())
                .andExpect(jsonPath("$.saldoPendiente").value(100.00))
                .andExpect(jsonPath("$.estadoMembresia").value("ACTIVA"));

        Long idPago = jdbcTemplate.queryForObject(
                "SELECT id_pago FROM pagos WHERE referencia = ?", Long.class,
                "ref-posterior");
        assertEquals(actor.getIdUsuario(), jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM pagos WHERE id_pago = ?", Long.class, idPago),
                "El responsable se obtiene de la autenticacion, no del cuerpo.");
        assertEquals("CONFIRMADO", jdbcTemplate.queryForObject(
                "SELECT estado FROM pagos WHERE id_pago = ?", String.class, idPago));
        assertEquals(CINCUENTA, jdbcTemplate.queryForObject(
                "SELECT monto FROM pagos WHERE id_pago = ?", BigDecimal.class, idPago));
        assertEquals("INICIAL", jdbcTemplate.queryForObject(
                "SELECT tipo_aplicacion FROM aplicaciones_pago_membresia "
                        + "WHERE id_pago = ?", String.class, idPago));
        assertEquals(new BigDecimal("100.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals(2L, contarPagosDelActor(),
                "El alta de la membresia crea un pago y el posterior otro.");
    }

    @Test
    void pagoRepetidoReproduceElMismoResultadoSinDuplicarFilas() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);
        String key = UUID.randomUUID().toString();
        var primera = mockMvc.perform(pagoConClave("ADMINISTRADOR", key,
                        "{\"monto\":100.00,\"metodoPago\":\"YAPE\","
                                + "\"referencia\":\"idem-posterior\","
                                + "\"observaciones\":\"misma solicitud\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode resultado = objectMapper.readTree(primera.getResponse().getContentAsString());
        String idOperacion = jdbcTemplate.queryForObject(
                "SELECT id_operacion FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);

        var repetida = mockMvc.perform(pagoConClave("ADMINISTRADOR", key,
                        "{\"monto\":100.00,\"metodoPago\":\"YAPE\","
                                + "\"referencia\":\"idem-posterior\","
                                + "\"observaciones\":\"misma solicitud\"}"))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(resultado, objectMapper.readTree(repetida.getResponse().getContentAsString()));

        mockMvc.perform(pagoConClave("ADMINISTRADOR", key,
                        "{\"monto\":90.00,\"metodoPago\":\"YAPE\","
                                + "\"referencia\":\"idem-posterior\","
                                + "\"observaciones\":\"misma solicitud\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/operaciones-pago/{id}", idOperacion)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMED"))
                .andExpect(jsonPath("$.idPago").value(resultado.get("idPago").asLong()))
                .andExpect(jsonPath("$.idAplicacion").value(resultado.get("idAplicacion").asLong()));

        assertEquals(2L, contarPagosDelActor());
        assertEquals(2L, contarAplicacionesDeLaMembresia());
        assertEquals(new BigDecimal("150.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(membresia.getIdMembresia()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                Long.class, actor.getIdUsuario(), key));
    }

    @Test
    void pagosPosterioresConcurrentesConLaMismaClaveCreanUnaAplicacion() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);
        String key = UUID.randomUUID().toString();
        CyclicBarrier inicio = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = executor.submit(() -> pagoConcurrente(inicio, key, CIEN));
            Future<Integer> b = executor.submit(() -> pagoConcurrente(inicio, key, CIEN));
            assertEquals(201, a.get(40, TimeUnit.SECONDS));
            assertEquals(201, b.get(40, TimeUnit.SECONDS));
        }
        assertEquals(2L, contarPagosDelActor());
        assertEquals(2L, contarAplicacionesDeLaMembresia());
        assertEquals(new BigDecimal("150.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(membresia.getIdMembresia()));
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key));
    }

    @Test
    void clavesDistintasCompitenPorSaldoYSoloUnaOperacionSeConfirma() throws Exception {
        crearMembresia(DOSCIENTOS, CIEN);
        String keyA = UUID.randomUUID().toString();
        String keyB = UUID.randomUUID().toString();
        CyclicBarrier inicio = new CyclicBarrier(2);
        int statusA;
        int statusB;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = executor.submit(() -> pagoConcurrente(inicio, keyA, SESENTA));
            Future<Integer> b = executor.submit(() -> pagoConcurrente(inicio, keyB, SESENTA));
            statusA = a.get(40, TimeUnit.SECONDS);
            statusB = b.get(40, TimeUnit.SECONDS);
        }
        assertTrue((statusA == 201 && statusB == 400) || (statusA == 400 && statusB == 201),
                "El pago que pierde la carrera responde la validación de saldo existente.");
        assertEquals(2L, contarPagosDelActor());
        assertEquals(2L, contarAplicacionesDeLaMembresia());
        assertEquals(new BigDecimal("160.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(membresia.getIdMembresia()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND estado = 'CONFIRMED'",
                Long.class, actor.getIdUsuario()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND estado = 'REJECTED'",
                Long.class, actor.getIdUsuario()));
    }

    private int pagoConcurrente(CyclicBarrier inicio, String key, BigDecimal amount)
            throws Exception {
        inicio.await(15, TimeUnit.SECONDS);
        String body = "{\"monto\":" + amount.toPlainString()
                + ",\"metodoPago\":\"EFECTIVO\"}";
        var response = mockMvc.perform(pagoConClave("ADMINISTRADOR", key, body)).andReturn()
                .getResponse();
        return response.getStatus();
    }

    @Test
    void registraComoFinalElPagoQueCierraElSaldo() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(new BigDecimal("150.00"), "ADMINISTRADOR", null, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.montoAplicado").value(150.00))
                .andExpect(jsonPath("$.saldoPendiente").value(0.00))
                .andExpect(jsonPath("$.estadoMembresia").value("ACTIVA"));

        assertEquals("FINAL", tipoDeUltimaAplicacion());
        assertEquals(0, saldoActual().compareTo(BigDecimal.ZERO));
    }

    @Test
    void elPagoParcialSeguidoDelCierreMarcaSoloElUltimoComoFinal() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(CIEN, "ADMINISTRADOR", null, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPendiente").value(50.00));
        assertEquals("INICIAL", tipoDeUltimaAplicacion());

        mockMvc.perform(pago(CINCUENTA, "ADMINISTRADOR", null, false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldoPendiente").value(0.00));
        assertEquals("FINAL", tipoDeUltimaAplicacion());

        assertEquals(1L, contarAplicacionesPorTipo("FINAL"));
        assertEquals(2L, contarAplicacionesPorTipo("INICIAL"),
                "El primer pago y el parcial conservan el tipo INICIAL.");
    }

    @Test
    void dosPagosSimultaneosDerivanElTipoDesdeElSaldoBloqueado() throws Exception {
        crearMembresia(DOSCIENTOS, CIEN);

        List<Boolean> resultados = ejecutarSimultaneamente(
                () -> registrarPosterior(CINCUENTA),
                () -> registrarPosterior(CINCUENTA));

        assertEquals(2L, resultados.stream().filter(Boolean::booleanValue).count(),
                "Ambos pagos caben en el saldo disponible.");
        assertEquals(new BigDecimal("200.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals(0, saldoActual().compareTo(BigDecimal.ZERO));
        assertEquals(1L, contarAplicacionesPorTipo("FINAL"),
                "Solo la aplicacion que cierra el saldo debe quedar como FINAL.");
        assertEquals(2L, contarAplicacionesPorTipo("INICIAL"));
        assertEquals(3L, contarPagosDelActor(),
                "Cada solicitud exitosa conserva exactamente un pago.");
    }

    @Test
    void dosPagosSimultaneosNoPermitenSobrepasarElSaldo() throws Exception {
        crearMembresia(DOSCIENTOS, CIEN);

        List<Boolean> resultados = ejecutarSimultaneamente(
                () -> registrarPosterior(SESENTA),
                () -> registrarPosterior(SESENTA));

        assertEquals(1L, resultados.stream().filter(Boolean::booleanValue).count(),
                "Solo un pago de S/ 60 cabe en el saldo de S/ 100.");
        assertEquals(new BigDecimal("160.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals(2L, contarAplicacionesDeLaMembresia());
        assertEquals(2L, contarPagosDelActor(),
                "El pago rechazado no debe quedar registrado.");
    }

    @Test
    void rechazaImportesNoPositivosSinCrearPagos() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(BigDecimal.ZERO, "ADMINISTRADOR", null, false))
                .andExpect(status().isBadRequest());
        mockMvc.perform(pago(new BigDecimal("-5.00"), "ADMINISTRADOR", null, false))
                .andExpect(status().isBadRequest());

        assertEquals(1L, contarPagosDelActor());
        assertEquals(1L, contarAplicacionesDeLaMembresia());
    }

    @Test
    void membresiaInexistenteResponde404SinCrearPagos() throws Exception {
        mockMvc.perform(pagoA(987654321L, new BigDecimal("10.00"),
                        "ADMINISTRADOR", null, false))
                .andExpect(status().isNotFound());

        assertEquals(0L, contarPagosDelActor());
    }

    @Test
    void rechazaMembresiasCanceladasYVencidasSinReactivarlas() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        actualizarEstado("CANCELADA");
        mockMvc.perform(pago(CINCUENTA, "ADMINISTRADOR", null, false))
                .andExpect(status().isConflict());
        assertEquals("CANCELADA", estadoActual());
        assertEquals(1L, contarPagosDelActor());

        actualizarEstado("VENCIDA");
        mockMvc.perform(pago(CINCUENTA, "ADMINISTRADOR", null, false))
                .andExpect(status().isConflict());
        assertEquals("VENCIDA", estadoActual());
        assertEquals(1L, contarPagosDelActor());
    }

    @Test
    void rechazaPagosDeMembresiasYaPagadas() throws Exception {
        crearMembresia(CINCUENTA, CINCUENTA);

        mockMvc.perform(pago(new BigDecimal("10.00"), "ADMINISTRADOR", null, false))
                .andExpect(status().isConflict());

        assertEquals(1L, contarPagosDelActor());
        assertEquals(0, saldoActual().compareTo(BigDecimal.ZERO));
    }

    @Test
    void rechazaSobrepagosConBadRequest() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(new BigDecimal("150.01"), "ADMINISTRADOR", null, false))
                .andExpect(status().isBadRequest());

        assertEquals(1L, contarPagosDelActor());
        assertEquals(1L, contarAplicacionesDeLaMembresia());
    }

    @Test
    void recepcionistaPuedeRegistrarPeroNutricionistaNo() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(pago(CINCUENTA, "RECEPCIONISTA", null, false))
                .andExpect(status().isCreated());
        assertEquals(2L, contarPagosDelActor());

        mockMvc.perform(pago(new BigDecimal("10.00"), "NUTRICIONISTA", null, false))
                .andExpect(status().isForbidden());
        mockMvc.perform(pago(new BigDecimal("10.00"), "ENTRENADOR", null, false))
                .andExpect(status().isForbidden());
        assertEquals(2L, contarPagosDelActor(),
                "Los roles sin permiso no deben crear pagos.");
    }

    @Test
    void sinAutenticacionResponde401() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        mockMvc.perform(post("/api/membresias/{idMembresia}/pagos",
                        membresia.getIdMembresia())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10.00,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(1L, contarPagosDelActor());
    }

    @Test
    void falloAlAplicarRevierteElPagoYLaAplicacion() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        doThrow(new DataIntegrityViolationException("Fallo de aplicacion inyectado"))
                .when(pagoService).aplicarPagoPosteriorAMembresia(
                        anyLong(), anyLong(), any(BigDecimal.class));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(pagoConClave("ADMINISTRADOR", key,
                        "{\"monto\":50.00,\"metodoPago\":\"YAPE\","
                                + "\"observaciones\":\"obs-posterior\"}"))
                .andExpect(status().isInternalServerError());

        assertEquals(1L, contarPagosDelActor(),
                "El pago creado dentro de la transaccion fallida debe revertirse.");
        assertEquals(1L, contarAplicacionesDeLaMembresia());
        assertEquals(new BigDecimal("50.00"),
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia()));
        assertEquals("ACTIVA", estadoActual());
        assertEquals("PENDING", estadoOperacion(key));
    }

    @Test
    void falloAlRegistrarElPagoRevierteLaOperacionCompleta() throws Exception {
        crearMembresia(DOSCIENTOS, CINCUENTA);

        doThrow(new DataIntegrityViolationException("Fallo de pago inyectado"))
                .when(pagoService).registrarPago(
                        anyLong(), any(BigDecimal.class), anyString(), any(), any());

        String key = UUID.randomUUID().toString();
        mockMvc.perform(pagoConClave("ADMINISTRADOR", key,
                        "{\"monto\":50.00,\"metodoPago\":\"YAPE\","
                                + "\"observaciones\":\"obs-posterior\"}"))
                .andExpect(status().isInternalServerError());

        assertEquals(1L, contarPagosDelActor());
        assertEquals(1L, contarAplicacionesDeLaMembresia());
        assertEquals("ACTIVA", estadoActual());
        assertEquals("PENDING", estadoOperacion(key));
    }

    @Test
    void elPrimerPagoQueCubreTodoElPrecioSigueSiendoInicial() throws Exception {
        crearSocioYPlan(CINCUENTA);

        mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idSocio\":" + socio.getIdSocio()
                                + ",\"idPlan\":" + plan.getIdPlan()
                                + ",\"montoPrimerPago\":50.00"
                                + ",\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        Membresia creada = membresiaRepository
                .findBySocio_IdSocio(socio.getIdSocio()).getFirst();
        assertEquals("INICIAL", tipoDeMembresia(creada.getIdMembresia()),
                "El primer pago conserva el tipo explicito del Bloque 9.");
        assertEquals(0, aplicacionRepository
                .sumarAplicacionesPorMembresia(creada.getIdMembresia())
                .compareTo(CINCUENTA));
    }

    private void crearMembresia(BigDecimal precio, BigDecimal primerPago) {
        crearSocioYPlan(precio);

        membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(precio);
        membresia.setFechaInicio(LocalDate.now());
        membresia.setFechaVencimiento(LocalDate.now().plusDays(30));
        membresia.setEstado("PENDIENTE_PAGO");
        membresia = membresiaRepository.saveAndFlush(membresia);

        Pago pago = pagoService.registrarPago(
                actor.getIdUsuario(), primerPago, "EFECTIVO", null, null);
        pagoService.aplicarPagoAMembresia(
                pago.getIdPago(), membresia.getIdMembresia(), primerPago,
                TipoAplicacionPago.INICIAL);
    }

    private void crearSocioYPlan(BigDecimal precio) {
        String token = token();

        socio = new Socio();
        socio.setCodigoSocio("IT-PP-" + token.substring(0, 10));
        socio.setNombres("Socio Integracion");
        socio.setApellidos("Pago Posterior " + token.substring(0, 8));
        socio.setEstado("ACTIVO");
        socio = socioRepository.saveAndFlush(socio);

        plan = new Plan();
        plan.setNombre("IT_PP_" + token.substring(0, 12));
        plan.setPrecio(precio);
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan = planRepository.saveAndFlush(plan);
    }

    private void actualizarEstado(String estado) {
        Membresia actual = membresiaRepository.findById(membresia.getIdMembresia())
                .orElseThrow();
        actual.setEstado(estado);
        membresiaRepository.saveAndFlush(actual);
    }

    private boolean registrarPosterior(BigDecimal monto) {
        try {
            membresiaService.registrarPagoPosterior(
                    membresia.getIdMembresia(),
                    new RegistrarPagoPosteriorRequest(monto, "EFECTIVO", null, null),
                    actor.getCorreo());
            return true;
        } catch (IllegalArgumentException | IllegalStateException
                | ConflictoException rechazado) {
            return false;
        }
    }

    private List<Boolean> ejecutarSimultaneamente(Callable<Boolean> primera,
            Callable<Boolean> segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier inicio = new CyclicBarrier(3);
        try {
            Future<Boolean> resultadoA = executor.submit(
                    () -> ejecutarTrasBarrera(inicio, primera));
            Future<Boolean> resultadoB = executor.submit(
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

    private Boolean ejecutarTrasBarrera(CyclicBarrier inicio,
            Callable<Boolean> operacion) throws Exception {
        inicio.await(20, TimeUnit.SECONDS);
        return operacion.call();
    }

    private MockHttpServletRequestBuilder pago(BigDecimal monto, String rol,
            String referencia, boolean idUsuarioFalso) {
        return pagoA(membresia.getIdMembresia(), monto, rol, referencia,
                idUsuarioFalso);
    }

    private MockHttpServletRequestBuilder pagoConClave(String rol, String key, String cuerpo) {
        return post("/api/membresias/" + membresia.getIdMembresia() + "/pagos")
                .header("Idempotency-Key", key)
                .with(user(actor.getCorreo()).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private MockHttpServletRequestBuilder pagoA(Long idMembresia, BigDecimal monto,
            String rol, String referencia, boolean idUsuarioFalso) {
        String cuerpo = "{\"monto\":" + monto.toPlainString()
                + ",\"metodoPago\":\"YAPE\""
                + (referencia == null ? "" : ",\"referencia\":\"" + referencia + "\"")
                + ",\"observaciones\":\"obs-posterior\""
                + (idUsuarioFalso ? ",\"idUsuario\":987654321" : "")
                + "}";
        return post("/api/membresias/" + idMembresia + "/pagos")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(user(actor.getCorreo()).roles(rol))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private long contarPagosDelActor() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
    }

    private String estadoOperacion(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);
    }

    private long contarAplicacionesDeLaMembresia() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ?", Long.class,
                membresia.getIdMembresia());
    }

    private long contarAplicacionesPorTipo(String tipo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ? AND tipo_aplicacion = ?",
                Long.class, membresia.getIdMembresia(), tipo);
    }

    private String tipoDeUltimaAplicacion() {
        return jdbcTemplate.queryForObject(
                "SELECT tipo_aplicacion FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ? ORDER BY id_aplicacion DESC LIMIT 1",
                String.class, membresia.getIdMembresia());
    }

    private String tipoDeMembresia(Long idMembresia) {
        return jdbcTemplate.queryForObject(
                "SELECT tipo_aplicacion FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ?", String.class, idMembresia);
    }

    private String estadoActual() {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM membresias WHERE id_membresia = ?",
                String.class, membresia.getIdMembresia());
    }

    private BigDecimal saldoActual() {
        BigDecimal totalAplicado =
                aplicacionRepository.sumarAplicacionesPorMembresia(
                        membresia.getIdMembresia());
        BigDecimal precioAcordado = membresiaRepository
                .findById(membresia.getIdMembresia())
                .orElseThrow()
                .getPrecioAcordado();
        return precioAcordado.subtract(totalAplicado);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
