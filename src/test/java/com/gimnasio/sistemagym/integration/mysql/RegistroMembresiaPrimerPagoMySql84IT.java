package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.PagoService;
import com.gimnasio.sistemagym.service.OperacionPagoIdempotenteService;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
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

@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class RegistroMembresiaPrimerPagoMySql84IT extends MySql84IntegrationTestSupport {

    private static final BigDecimal PRECIO_PLAN = new BigDecimal("200.00");
    private static final BigDecimal PRIMER_PAGO = new BigDecimal("50.00");

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private OperacionPagoIdempotenteService operacionService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private AplicacionPagoMembresiaRepository aplicacionRepository;

    @MockitoSpyBean
    private PagoService pagoService;

    private Rol rol;
    private Usuario actor;
    private Socio socio;
    private Plan plan;

    @BeforeEach
    void crearActorDePrueba() {
        rol = new Rol();
        rol.setNombre("ADMINISTRADOR");
        rol = rolRepository.saveAndFlush(rol);

        actor = new Usuario();
        actor.setRol(rol);
        actor.setNombres("Actor Integracion");
        actor.setApellidos("Registro Membresia");
        actor.setCorreo("actor-" + token() + "@example.test");
        actor.setContrasenaHash("fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarFixturesPropiosEnOrdenDeClavesForaneas() {
        if (actor != null) {
            jdbcTemplate.update("DELETE FROM operaciones_pago_idempotentes WHERE id_usuario = ?",
                    actor.getIdUsuario());
        }
        if (socio != null && actor != null) {
            jdbcTemplate.update("""
                    DELETE FROM aplicaciones_pago_membresia
                    WHERE id_membresia IN (
                        SELECT id_membresia FROM membresias WHERE id_socio = ?
                    ) OR id_pago IN (
                        SELECT id_pago FROM pagos WHERE id_usuario = ?
                    )
                    """, socio.getIdSocio(), actor.getIdUsuario());
            jdbcTemplate.update("DELETE FROM membresias WHERE id_socio = ?",
                    socio.getIdSocio());
        }
        if (actor != null) {
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
    void registraMembresiaPagoYAplicacionEnUnaSolaOperacion() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");

        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMembresia").isNumber())
                .andExpect(jsonPath("$.idPago").isNumber())
                .andExpect(jsonPath("$.idAplicacion").isNumber())
                .andExpect(jsonPath("$.precioAcordado").value(200.00))
                .andExpect(jsonPath("$.montoPrimerPago").value(50.00))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.usuario").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        Membresia membresia = membresiaRepository
                .findBySocio_IdSocio(socio.getIdSocio()).getFirst();
        assertEquals(new BigDecimal("200.00"), membresia.getPrecioAcordado());
        assertEquals("ACTIVA", membresia.getEstado());
        assertNotNull(membresia.getFechaLimitePago(),
                "Se conserva el cálculo de fecha límite actual al llegar al mínimo.");

        Long cantidadPagos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
        Long idPago = jdbcTemplate.queryForObject(
                "SELECT id_pago FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
        Long cantidadAplicaciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia WHERE id_pago = ?",
                Long.class, idPago);
        BigDecimal montoPago = jdbcTemplate.queryForObject(
                "SELECT monto FROM pagos WHERE id_pago = ?", BigDecimal.class, idPago);
        Long fechaPagoGenerada = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_pago = ? AND fecha_pago IS NOT NULL",
                Long.class, idPago);
        String estadoPago = jdbcTemplate.queryForObject(
                "SELECT estado FROM pagos WHERE id_pago = ?", String.class, idPago);
        String metodoPago = jdbcTemplate.queryForObject(
                "SELECT metodo_pago FROM pagos WHERE id_pago = ?", String.class, idPago);
        BigDecimal montoAplicado = aplicacionRepository.sumarAplicacionesPorPago(idPago);
        Long responsable = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM pagos WHERE id_pago = ?", Long.class, idPago);
        String tipo = jdbcTemplate.queryForObject(
                "SELECT tipo_aplicacion FROM aplicaciones_pago_membresia WHERE id_pago = ?",
                String.class, idPago);

        assertEquals(1L, cantidadPagos, "El alta crea un único pago.");
        assertEquals(1L, cantidadAplicaciones, "El pago tiene una única aplicación.");
        assertEquals(montoPago, montoAplicado,
                "El importe se conserva una sola vez en la trazabilidad de aplicación.");
        assertEquals(PRIMER_PAGO, montoPago);
        assertEquals(1L, fechaPagoGenerada, "El pago debe conservar su fecha.");
        assertEquals("CONFIRMADO", estadoPago);
        assertEquals("EFECTIVO", metodoPago);
        assertEquals(actor.getIdUsuario(), responsable,
                "El responsable se resuelve desde el correo autenticado.");
        assertEquals(TipoAplicacionPago.INICIAL.name(), tipo);

        plan.setPrecio(new BigDecimal("999.00"));
        planRepository.saveAndFlush(plan);
        assertEquals(new BigDecimal("200.00"), membresiaRepository
                .findById(membresia.getIdMembresia()).orElseThrow()
                .getPrecioAcordado(), "La membresía conserva el precio original.");
    }

    @Test
    void recuperaPorClaveUnPostConfirmadoCuyaRespuestaSeDescarto() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        String key = UUID.randomUUID().toString(); // único dato persistido del intento junto con la sesión/solicitud

        var respuestaPostPerdida = mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false, key))
                .andExpect(status().isCreated()).andReturn();
        JsonNode original = objectMapper.readTree(respuestaPostPerdida.getResponse().getContentAsString());
        // El cliente simulado descarta deliberadamente la respuesta y solo conserva la clave.
        respuestaPostPerdida = null;
        long membresiasAntes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM membresias WHERE id_socio = ?",
                Long.class, socio.getIdSocio());
        long pagosAntes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pagos WHERE id_usuario = ?",
                Long.class, actor.getIdUsuario());
        long aplicacionesAntes = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM aplicaciones_pago_membresia",
                Long.class);

        var recuperacion = mockMvc.perform(get("/api/operaciones-pago/por-clave/{clave}", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMED"))
                .andExpect(jsonPath("$.resultado.idMembresia").value(original.get("idMembresia").asLong()))
                .andExpect(jsonPath("$.resultado.idPago").value(original.get("idPago").asLong()))
                .andExpect(jsonPath("$.idMembresia").value(original.get("idMembresia").asLong()))
                .andExpect(jsonPath("$.idPago").value(original.get("idPago").asLong()))
                .andReturn();
        assertEquals(original, objectMapper.readTree(
                objectMapper.readTree(recuperacion.getResponse().getContentAsString())
                        .get("resultado").toString()));

        // Un reintento con la misma clave reproduce la respuesta confirmada, sin ejecutar negocio otra vez.
        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false, key))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMembresia").value(original.get("idMembresia").asLong()))
                .andExpect(jsonPath("$.idPago").value(original.get("idPago").asLong()));
        assertEquals(membresiasAntes, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class, socio.getIdSocio()));
        assertEquals(pagosAntes, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class, actor.getIdUsuario()));
        assertEquals(aplicacionesAntes, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia", Long.class));
    }

    @Test
    void altaRepetidaReproduceResultadoYRechazaReutilizacionDeClave() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        String key = UUID.randomUUID().toString();
        String body = cuerpoAlta(PRIMER_PAGO);

        var primera = mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        JsonNode resultadoPrimero = objectMapper.readTree(
                primera.getResponse().getContentAsString());
        String idOperacion = jdbcTemplate.queryForObject(
                "SELECT id_operacion FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);

        var repetida = mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        assertEquals(resultadoPrimero, objectMapper.readTree(
                repetida.getResponse().getContentAsString()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/operaciones-pago/{id}", idOperacion)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONFIRMED"))
                .andExpect(jsonPath("$.idMembresia").value(
                        resultadoPrimero.get("idMembresia").asLong()))
                .andExpect(jsonPath("$.idPago").value(resultadoPrimero.get("idPago").asLong()))
                .andExpect(jsonPath("$.idAplicacion").value(
                        resultadoPrimero.get("idAplicacion").asLong()));

        mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(new BigDecimal("60.00"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/membresias/{id}/pagos",
                        resultadoPrimero.get("idMembresia").asLong())
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\":10.00,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isConflict());

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class,
                socio.getIdSocio()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia WHERE id_membresia = ?",
                Long.class, resultadoPrimero.get("idMembresia").asLong()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                Long.class, actor.getIdUsuario(), key));
    }

    @Test
    void altasConcurrentesConLaMismaClaveConfirmanUnSoloResultado() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        String key = UUID.randomUUID().toString();
        CyclicBarrier inicio = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> altaConcurrente(inicio, key));
            var b = executor.submit(() -> altaConcurrente(inicio, key));
            assertEquals(201, a.get(40, TimeUnit.SECONDS));
            assertEquals(201, b.get(40, TimeUnit.SECONDS));
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class,
                socio.getIdSocio()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia", Long.class));
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key));
    }

    @Test
    void tresAltasConcurrentesConLaMismaClavePersistenUnaSolaOperacion() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        String key = UUID.randomUUID().toString();
        CyclicBarrier inicio = new CyclicBarrier(3);
        try (var executor = Executors.newFixedThreadPool(3)) {
            var a = executor.submit(() -> altaConcurrente(inicio, key));
            var b = executor.submit(() -> altaConcurrente(inicio, key));
            var c = executor.submit(() -> altaConcurrente(inicio, key));
            assertEquals(201, a.get(40, TimeUnit.SECONDS));
            assertEquals(201, b.get(40, TimeUnit.SECONDS));
            assertEquals(201, c.get(40, TimeUnit.SECONDS));
        }
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class,
                socio.getIdSocio()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia", Long.class));
        assertEquals("CONFIRMED", jdbcTemplate.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key));
    }

    private int altaConcurrente(CyclicBarrier inicio, String key) throws Exception {
        inicio.await(15, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoAlta(PRIMER_PAGO)))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void solicitudPendienteEsConsultableYElRechazoDeterministaQuedaPersistido() throws Exception {
        crearSocio("INACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        String key = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(PRIMER_PAGO)))
                .andExpect(status().isConflict());
        String rechazoTipo = jdbcTemplate.queryForObject(
                "SELECT rechazo_tipo FROM operaciones_pago_idempotentes WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);
        String rechazoMensaje = jdbcTemplate.queryForObject(
                "SELECT rechazo_mensaje FROM operaciones_pago_idempotentes WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);
        long pagosAntesConsulta = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pagos", Long.class);
        mockMvc.perform(get("/api/operaciones-pago/por-clave/{clave}", key)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REJECTED"))
                .andExpect(jsonPath("$.rechazoTipo").value(rechazoTipo))
                .andExpect(jsonPath("$.rechazoMensaje").value(rechazoMensaje))
                .andExpect(jsonPath("$.resultado").doesNotExist());
        assertEquals(pagosAntesConsulta, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pagos", Long.class));
        String id = jdbcTemplate.queryForObject(
                "SELECT id_operacion FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/operaciones-pago/{id}", id)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REJECTED"));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class,
                socio.getIdSocio()));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));

        String pendingKey = UUID.randomUUID().toString();
        var pending = operacionService.reservar(actor.getCorreo(), UUID.fromString(pendingKey),
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "pending-test");
        mockMvc.perform(get("/api/operaciones-pago/por-clave/{clave}", pendingKey)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDING"))
                .andExpect(jsonPath("$.resultado").doesNotExist());
    }

    @Test
    void recuperaUnaReservaPendienteEjecutandoElPostOriginal() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        UUID key = UUID.randomUUID();
        String canonical = "MEMBRESIA_INICIAL|" + socio.getIdSocio() + "|" + plan.getIdPlan()
                + "|50|EFECTIVO||";
        var reserva = operacionService.reservar(actor.getCorreo(), key,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, canonical);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/operaciones-pago/{id}", reserva.idOperacion())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDING"));

        mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", key.toString())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoAlta(PRIMER_PAGO)))
                .andExpect(status().isCreated());

        assertEquals("CONFIRMED", estadoOperacion(key.toString()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Long.class,
                socio.getIdSocio()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia", Long.class));
    }

    @Test
    void rechazaPrimerPagoMenorA40() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");

        mockMvc.perform(registrar(new BigDecimal("39.99"), "ADMINISTRADOR", false))
                .andExpect(status().isBadRequest());
        assertSinMembresiaNiPago();
    }

    @Test
    void rechazaPrimerPagoSuperiorAlSaldoPendiente() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");

        mockMvc.perform(registrar(new BigDecimal("200.01"), "ADMINISTRADOR", false))
                .andExpect(status().isBadRequest());
        assertSinMembresiaNiPago();
    }

    @Test
    void devuelve404SiSocioOPlanNoExisten() throws Exception {
        crearPlan(PRECIO_PLAN, "ACTIVO");
        mockMvc.perform(registrarConIds(987654321L, plan.getIdPlan(),
                        PRIMER_PAGO, "ADMINISTRADOR"))
                .andExpect(status().isNotFound());

        crearSocio("ACTIVO");
        mockMvc.perform(registrarConIds(socio.getIdSocio(), 987654321L,
                        PRIMER_PAGO, "ADMINISTRADOR"))
                .andExpect(status().isNotFound());
        assertSinMembresiaNiPago();
    }

    @Test
    void devuelve409SiSocioOPlanEstanInactivos() throws Exception {
        crearSocio("INACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false))
                .andExpect(status().isConflict());

        socio.setEstado("ACTIVO");
        socioRepository.saveAndFlush(socio);
        plan.setEstado("INACTIVO");
        planRepository.saveAndFlush(plan);
        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false))
                .andExpect(status().isConflict());
        assertSinMembresiaNiPago();
    }

    @Test
    void recibeElResponsableDeLaAutenticacionAunqueElClienteEnvieOtroId() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");

        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", true))
                .andExpect(status().isCreated());

        Long idResponsable = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
        assertEquals(actor.getIdUsuario(), idResponsable);
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos", Long.class));
    }

    @Test
    void recepcionistaPuedeRegistrarPeroNutricionistaNo() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        mockMvc.perform(registrar(PRIMER_PAGO, "RECEPCIONISTA", false))
                .andExpect(status().isCreated());
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario()));
    }

    @Test
    void falloAlCrearPagoRevierteLaMembresia() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        doThrow(new DataIntegrityViolationException("Fallo de pago inyectado"))
                .when(pagoService).registrarPago(
                        eq(actor.getIdUsuario()), eq(PRIMER_PAGO), eq("EFECTIVO"),
                        eq((String) null), eq((String) null));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false, key))
                .andExpect(status().isInternalServerError());
        assertSinMembresiaNiPago();
        assertEquals("PENDING", estadoOperacion(key));
    }

    @Test
    void falloAlAplicarPagoRevierteMembresiaYPago() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");
        doThrow(new DataIntegrityViolationException("Fallo de aplicacion inyectado"))
                .when(pagoService).aplicarPagoAMembresia(
                        anyLong(), anyLong(), eq(PRIMER_PAGO),
                        eq(TipoAplicacionPago.INICIAL));

        String key = UUID.randomUUID().toString();
        mockMvc.perform(registrar(PRIMER_PAGO, "ADMINISTRADOR", false, key))
                .andExpect(status().isInternalServerError());
        assertSinMembresiaNiPago();
        assertEquals("PENDING", estadoOperacion(key));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia", Long.class));
    }

    @Test
    void nutricionistaNoPuedeRegistrarMembresias() throws Exception {
        crearSocio("ACTIVO");
        crearPlan(PRECIO_PLAN, "ACTIVO");

        mockMvc.perform(registrar(PRIMER_PAGO, "NUTRICIONISTA", false))
                .andExpect(status().isForbidden());
        mockMvc.perform(registrar(PRIMER_PAGO, "ENTRENADOR", false))
                .andExpect(status().isForbidden());
        assertSinMembresiaNiPago();
    }

    private void crearSocio(String estado) {
        String token = token();
        socio = new Socio();
        socio.setCodigoSocio("IT-MEM-" + token.substring(0, 10));
        socio.setNombres("Socio Integracion");
        socio.setApellidos("Membresia " + token.substring(0, 8));
        socio.setEstado(estado);
        socio = socioRepository.saveAndFlush(socio);
    }

    private void crearPlan(BigDecimal precio, String estado) {
        plan = new Plan();
        plan.setNombre("IT_MEM_" + token().substring(0, 12));
        plan.setPrecio(precio);
        plan.setDuracionDias(30);
        plan.setEstado(estado);
        plan = planRepository.saveAndFlush(plan);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registrar(
            BigDecimal monto, String rolAutenticado, boolean idResponsableFalso) {
        return registrar(monto, rolAutenticado, idResponsableFalso, UUID.randomUUID().toString());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registrar(
            BigDecimal monto, String rolAutenticado, boolean idResponsableFalso, String key) {
        String cuerpo = "{\"idSocio\":" + socio.getIdSocio()
                + ",\"idPlan\":" + plan.getIdPlan()
                + ",\"montoPrimerPago\":" + monto.toPlainString()
                + ",\"metodoPago\":\"EFECTIVO\""
                + (idResponsableFalso ? ",\"idUsuario\":987654321" : "")
                + (idResponsableFalso
                        ? ",\"referencia\":\"ref-fixture\",\"observaciones\":\"obs-fixture\""
                        : "")
                + "}";
        return post("/api/membresias")
                .header("Idempotency-Key", key)
                .with(user(actor.getCorreo()).roles(rolAutenticado))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String estadoOperacion(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes "
                        + "WHERE id_usuario = ? AND clave_idempotencia = ?",
                String.class, actor.getIdUsuario(), key);
    }

    private String cuerpoAlta(BigDecimal monto) {
        return "{\"idSocio\":" + socio.getIdSocio()
                + ",\"idPlan\":" + plan.getIdPlan()
                + ",\"montoPrimerPago\":" + monto.toPlainString()
                + ",\"metodoPago\":\"EFECTIVO\"}";
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registrarConIds(
            Long idSocio, Long idPlan, BigDecimal monto, String rolAutenticado) {
        return post("/api/membresias")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(user(actor.getCorreo()).roles(rolAutenticado))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idSocio\":" + idSocio
                        + ",\"idPlan\":" + idPlan
                        + ",\"montoPrimerPago\":" + monto.toPlainString()
                        + ",\"metodoPago\":\"EFECTIVO\"}");
    }

    private void assertSinMembresiaNiPago() {
        long membresias = socio == null ? 0 : membresiaRepository
                .findBySocio_IdSocio(socio.getIdSocio()).size();
        long pagos = actor == null ? 0 : jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Long.class,
                actor.getIdUsuario());
        assertEquals(0L, membresias);
        assertEquals(0L, pagos);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
