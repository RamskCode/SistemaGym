package com.gimnasio.sistemagym.integration.mysql;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
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

import com.gimnasio.sistemagym.dto.RegistrarIngresoRequest;
import com.gimnasio.sistemagym.entity.ContactoPromocional;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.ContactoPromocionalRepository;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.ConfiguracionService;
import com.gimnasio.sistemagym.service.IngresoService;
import com.gimnasio.sistemagym.service.PagoService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class RegistroIngresosMySql84IT extends MySql84IntegrationTestSupport {

    private static final String CLAVE_PRECIO = "PRECIO_PASE_DIARIO";
    private static final String PRECIO_INICIAL = "8.00";
    private static final BigDecimal PRECIO_INICIAL_DECIMAL =
            new BigDecimal(PRECIO_INICIAL);
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RolRepository rolRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SocioRepository socioRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private MembresiaRepository membresiaRepository;
    @Autowired private ContactoPromocionalRepository contactoRepository;
    @Autowired private IngresoRepository ingresoRepository;
    @Autowired private IngresoService ingresoService;
    @Autowired private ConfiguracionService configuracionService;

    @MockitoSpyBean
    private PagoService pagoService;

    @MockitoSpyBean
    private IngresoRepository ingresoRepositorySpy;

    private Rol rol;
    private Usuario actor;
    private final List<Long> idsSocios = new ArrayList<>();
    private final List<Long> idsPlanes = new ArrayList<>();
    private final List<String[]> documentosContacto = new ArrayList<>();

    @BeforeEach
    void crearActorDePrueba() {
        rol = new Rol();
        rol.setNombre("RECEPCIONISTA");
        rol = rolRepository.saveAndFlush(rol);

        actor = new Usuario();
        actor.setRol(rol);
        actor.setNombres("Actor Integracion");
        actor.setApellidos("Registro Ingresos");
        actor.setCorreo("actor-" + token() + "@example.test");
        actor.setContrasenaHash("fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarFixturesPropiosEnOrdenDeClavesForaneas() {
        if (actor != null) {
            jdbcTemplate.update("UPDATE ingresos SET id_pase_origen = NULL WHERE id_usuario = ?",
                    actor.getIdUsuario());
            jdbcTemplate.update("DELETE FROM ingresos WHERE id_usuario = ?",
                    actor.getIdUsuario());
        }

        for (Long idSocio : idsSocios) {
            jdbcTemplate.update("""
                    DELETE FROM aplicaciones_pago_membresia
                    WHERE id_membresia IN (
                        SELECT id_membresia FROM membresias WHERE id_socio = ?
                    )
                    """, idSocio);
            jdbcTemplate.update("DELETE FROM notificaciones WHERE id_socio = ?",
                    idSocio);
            jdbcTemplate.update("DELETE FROM membresias WHERE id_socio = ?",
                    idSocio);
        }

        if (actor != null) {
            jdbcTemplate.update("""
                    DELETE FROM aplicaciones_pago_membresia
                    WHERE id_pago IN (
                        SELECT id_pago FROM pagos WHERE id_usuario = ?
                    )
                    """, actor.getIdUsuario());
            jdbcTemplate.update("DELETE FROM pagos WHERE id_usuario = ?",
                    actor.getIdUsuario());
        }

        for (String[] documento : documentosContacto) {
            jdbcTemplate.update(
                    "DELETE FROM contactos_promocionales "
                            + "WHERE tipo_documento = ? AND numero_documento = ?",
                    documento[0], documento[1]);
        }
        for (Long idSocio : idsSocios) {
            jdbcTemplate.update("DELETE FROM socios WHERE id_socio = ?", idSocio);
        }
        for (Long idPlan : idsPlanes) {
            jdbcTemplate.update("DELETE FROM planes WHERE id_plan = ?", idPlan);
        }
        if (actor != null) {
            usuarioRepository.deleteById(actor.getIdUsuario());
        }
        if (rol != null) {
            rolRepository.deleteById(rol.getIdRol());
        }

        idsSocios.clear();
        idsPlanes.clear();
        documentosContacto.clear();

        jdbcTemplate.update(
                "UPDATE configuraciones SET valor = ? WHERE clave = ?",
                PRECIO_INICIAL, CLAVE_PRECIO);
    }

    @Test
    void registraIngresoDeSocioConMembresiaActivaSinCobrarPase() throws Exception {
        Socio socio = crearSocio("ACTIVO", dniValido());
        crearMembresia(socio, "ACTIVA",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(29));

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIngreso").isNumber())
                .andExpect(jsonPath("$.tipoIngreso").value("SOCIO"))
                .andExpect(jsonPath("$.idSocio").isNumber())
                .andExpect(jsonPath("$.idVisitante").value(nullValue()))
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumento").value(socio.getDni()))
                .andExpect(jsonPath("$.fechaHora").isNotEmpty())
                .andExpect(jsonPath("$.paseDiarioCobrado").value(false))
                .andExpect(jsonPath("$.montoCobrado").value(nullValue()))
                .andExpect(jsonPath("$.idPago").value(nullValue()))
                .andExpect(jsonPath("$.resultado").value("ACEPTADO"));

        assertEquals(1L, contarIngresos());
        assertEquals(0L, contarPagos());
    }

    @Test
    void rechazaSocioInactivo() throws Exception {
        Socio socio = crearSocio("INACTIVO", dniValido());
        crearMembresia(socio, "ACTIVA",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(29));

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isConflict());

        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
    }

    @Test
    void rechazaMembresiaVencida() throws Exception {
        Socio socio = crearSocio("ACTIVO", dniValido());
        crearMembresia(socio, "ACTIVA",
                LocalDate.now().minusDays(40), LocalDate.now().minusDays(1));

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isConflict());

        assertEquals(0L, contarIngresos());
    }

    @Test
    void permiteIngresoDuranteElUltimoDiaDeVigencia() throws Exception {
        LocalDate hoyEnLima = LocalDate.now(ZoneId.of("America/Lima"));
        Socio socio = crearSocio("ACTIVO", dniValido());
        crearMembresia(socio, "ACTIVA", hoyEnLima.minusDays(30), hoyEnLima);

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoIngreso").value("SOCIO"));

        assertEquals(1L, contarIngresos());
        assertEquals(0L, contarPagos());
    }

    @Test
    void rechazaMembresiaCancelada() throws Exception {
        Socio socio = crearSocio("ACTIVO", dniValido());
        crearMembresia(socio, "CANCELADA",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(29));

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isConflict());

        assertEquals(0L, contarIngresos());
    }

    @Test
    void rechazaSocioSinDni() throws Exception {
        Socio socio = crearSocio("ACTIVO", null);
        crearMembresia(socio, "ACTIVA",
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(29));

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoSocioPorId(socio.getIdSocio(), dniValido())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje",
                        containsString("no corresponde al socio")));

        assertEquals(0L, contarIngresos());
    }

    @Test
    void registraVisitanteConDniYPagaPaseDiario() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("DNI", dni, "Visitante DNI",
                                "999888777", "", "EFECTIVO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idIngreso").isNumber())
                .andExpect(jsonPath("$.tipoIngreso").value("VISITANTE"))
                .andExpect(jsonPath("$.idSocio").value(nullValue()))
                .andExpect(jsonPath("$.idVisitante").isNumber())
                .andExpect(jsonPath("$.tipoDocumento").value("DNI"))
                .andExpect(jsonPath("$.numeroDocumento").value(dni))
                .andExpect(jsonPath("$.paseDiarioCobrado").value(true))
                .andExpect(jsonPath("$.montoCobrado")
                        .value(PRECIO_INICIAL_DECIMAL.doubleValue()))
                .andExpect(jsonPath("$.idPago").isNumber())
                .andExpect(jsonPath("$.resultado").value("ACEPTADO"));

        assertEquals(1L, contarIngresos());
        assertEquals(1L, contarPagos());
        assertEquals(0, montoDelPagoUnico().compareTo(PRECIO_INICIAL_DECIMAL),
                "El pago debe usar el precio configurado.");
        assertEquals(1L, contarContactos("DNI", dni));

    }

    @Test
    void rechazaCeAlfanumericoSinCrearContactoIngresoNiPago()
            throws Exception {
        recordarDocumento("CE", "AB-123");

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("ce", "  ab-123  ", "Visitante CE",
                                "988777666", null, "YAPE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("1 y 12 dígitos")));

        assertEquals(0L, contarContactos("CE", "AB-123"));
        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
    }

    @Test
    void reutilizaVisitanteSinSobrescribirSusDatosPersonales() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);

        ContactoPromocional existente = new ContactoPromocional();
        existente.setTipoDocumento(TipoDocumento.DNI);
        existente.setNumeroDocumento(dni);
        existente.setNombre("Nombre Original");
        existente.setCelular("111222333");
        existente.setCorreo("original@example.test");
        existente = contactoRepository.saveAndFlush(existente);

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("DNI", dni, "Nombre Distinto",
                                "999999999", "distinto@example.test", "EFECTIVO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idVisitante")
                        .value(existente.getIdContacto().intValue()))
                .andExpect(jsonPath("$.numeroDocumento").value(dni));

        assertEquals(1L, contarContactos("DNI", dni));

        ContactoPromocional recargado = contactoRepository
                .findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, dni)
                .orElseThrow();
        assertEquals("Nombre Original", recargado.getNombre());
        assertEquals("111222333", recargado.getCelular());
        assertEquals("original@example.test", recargado.getCorreo());
        assertEquals(1L, contarPagos());
    }

    @Test
    void rechazaDocumentosInvalidos() throws Exception {
        List<String> cuerposInvalidos = List.of(
                cuerpoVisitante("DNI", "1234567", "Corto", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("DNI", "1234567A", "Letras", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("DNI", "1234 5678", "Espacio", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("CE", "AB12", "Corto", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("CE", "AB 123", "Espacio", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("CE", "AB_123", "Caracter", "999888777", null, "EFECTIVO"),
                cuerpoVisitante("CE", "AB123456789012345678901", "Largo",
                        "999888777", null, "EFECTIVO"),
                cuerpoVisitante("PASAPORTE", "12345678", "Tipo", "999888777",
                        null, "EFECTIVO"));

        for (String cuerpo : cuerposInvalidos) {
            mockMvc.perform(registrar("RECEPCIONISTA", cuerpo))
                    .andExpect(status().isBadRequest());
        }

        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
    }

    @Test
    void rechazaSegundoIngresoDelMismoDocumentoEnElDia() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);
        String cuerpo = cuerpoVisitante("DNI", dni, "Visitante Duplicado",
                "999888777", null, "EFECTIVO");

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpo))
                .andExpect(status().isCreated());
        mockMvc.perform(registrar("RECEPCIONISTA", cuerpo))
                .andExpect(status().isConflict());

        assertEquals(1L, contarIngresos());
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarContactos("DNI", dni));
    }

    @Test
    void nutricionistaNoPuedeRegistrarIngresos() throws Exception {
        String dni = dniValido();

        mockMvc.perform(registrar("NUTRICIONISTA",
                        cuerpoVisitante("DNI", dni, "Visitante Nutricionista",
                                "999888777", null, "EFECTIVO")))
                .andExpect(status().isForbidden());

        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
        assertEquals(0L, contarContactos("DNI", dni));
    }

    @Test
    void usaElPrecioVigenteConfiguradoDelPaseDiario() throws Exception {
        configuracionService.actualizar(CLAVE_PRECIO, "12.50");
        String dni = dniValido();
        recordarDocumento("DNI", dni);

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("DNI", dni, "Visitante Precio",
                                "999888777", null, "EFECTIVO")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paseDiarioCobrado").value(true))
                .andExpect(jsonPath("$.montoCobrado").value(12.50));

        assertEquals(0, montoDelPagoUnico().compareTo(new BigDecimal("12.50")),
                "El pago debe reflejar el precio configurado, no un valor fijo.");
    }

    @Test
    void revierteTodoSiFallaElRegistroDelPago() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);

        doThrow(new DuplicateKeyException(
                "Duplicate entry 'fixture' for key 'pagos.PRIMARY'"))
                .when(pagoService).registrarPago(
                        anyLong(), any(BigDecimal.class), anyString(),
                        any(), any());

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("DNI", dni, "Visitante Rollback Pago",
                                "999888777", null, "EFECTIVO")))
                .andExpect(status().isConflict());

        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
        assertEquals(0L, contarContactos("DNI", dni));
    }

    @Test
    void revierteTodoSiFallaElRegistroDelIngreso() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);

        doThrow(new DuplicateKeyException(
                "Duplicate entry 'fixture' for key 'ingresos.uk_ingresos_documento_fecha'"))
                .when(ingresoRepositorySpy).saveAndFlush(any(Ingreso.class));

        mockMvc.perform(registrar("RECEPCIONISTA",
                        cuerpoVisitante("DNI", dni, "Visitante Rollback Ingreso",
                                "999888777", null, "EFECTIVO")))
                .andExpect(status().isConflict());

        assertEquals(0L, contarIngresos());
        assertEquals(0L, contarPagos());
        assertEquals(0L, contarContactos("DNI", dni));
    }

    @Test
    void evitaIngresosYPagosDuplicadosBajoConcurrencia() throws Exception {
        String dni = dniValido();
        recordarDocumento("DNI", dni);
        RegistrarIngresoRequest solicitud = new RegistrarIngresoRequest(
                "DNI", dni, null, "Visitante", "Concurrente", "999888777",
                null, "EFECTIVO", null, null);

        List<Boolean> resultados = ejecutarSimultaneamente(
                () -> registrarIngresoConcurrente(solicitud),
                () -> registrarIngresoConcurrente(solicitud));

        assertEquals(1L, resultados.stream()
                        .filter(Boolean::booleanValue).count(),
                "Solo una solicitud concurrente puede registrar el ingreso.");
        assertEquals(1L, contarIngresos());
        assertEquals(1L, contarPagos());
        assertEquals(1L, contarContactos("DNI", dni));
    }

    @Test
    void socioConMembresiaValidaNoGeneraPagoDiarioNiAplicacion()
            throws Exception {
        Socio socio = crearSocio("ACTIVO", dniValido());
        Membresia membresia = crearMembresia(socio, "ACTIVA",
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(28));

        mockMvc.perform(registrar("RECEPCIONISTA", cuerpoSocio(socio.getDni())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoIngreso").value("SOCIO"))
                .andExpect(jsonPath("$.paseDiarioCobrado").value(false))
                .andExpect(jsonPath("$.montoCobrado").value(nullValue()))
                .andExpect(jsonPath("$.idPago").value(nullValue()));

        assertEquals(0L, contarPagos());
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM aplicaciones_pago_membresia "
                        + "WHERE id_membresia = ?",
                Long.class, membresia.getIdMembresia()));
        assertEquals(0L, contarContactos("DNI", socio.getDni()));
    }

    private boolean registrarIngresoConcurrente(RegistrarIngresoRequest solicitud) {
        try {
            ingresoService.registrar(solicitud, actor.getCorreo());
            return true;
        } catch (ConflictoException | DataIntegrityViolationException ex) {
            return false;
        }
    }

    private List<Boolean> ejecutarSimultaneamente(Callable<Boolean> primera,
            Callable<Boolean> segunda) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier inicio = new CyclicBarrier(3);
        try {
            Future<Boolean> resultadoA =
                    executor.submit(() -> ejecutarTrasBarrera(inicio, primera));
            Future<Boolean> resultadoB =
                    executor.submit(() -> ejecutarTrasBarrera(inicio, segunda));
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

    private Socio crearSocio(String estado, String dni) {
        Socio socio = new Socio();
        socio.setCodigoSocio("IT-ING-" + tokenCorto());
        socio.setDni(dni);
        socio.setNombres("Socio Ingreso");
        socio.setApellidos("Integracion " + tokenCorto());
        socio.setEstado(estado);
        socio = socioRepository.saveAndFlush(socio);
        idsSocios.add(socio.getIdSocio());
        return socio;
    }

    private Membresia crearMembresia(Socio socio, String estado,
            LocalDate inicio, LocalDate vencimiento) {
        Plan plan = new Plan();
        plan.setNombre("IT_ING_" + tokenCorto());
        plan.setPrecio(new BigDecimal("100.00"));
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan = planRepository.saveAndFlush(plan);
        idsPlanes.add(plan.getIdPlan());

        Membresia membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(new BigDecimal("100.00"));
        membresia.setFechaInicio(inicio);
        membresia.setFechaVencimiento(vencimiento);
        membresia.setEstado(estado);
        return membresiaRepository.saveAndFlush(membresia);
    }

    private MockHttpServletRequestBuilder registrar(
            String rolAutenticado, String cuerpo) {
        return post("/api/ingresos")
                .with(user(actor.getCorreo()).roles(rolAutenticado))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private String cuerpoSocio(String dni) {
        return "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"" + dni + "\"}";
    }

    private String cuerpoSocioPorId(Long idSocio, String dni) {
        return "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"" + dni
                + "\",\"idSocio\":" + idSocio + "}";
    }

    private String cuerpoVisitante(String tipoDocumento, String numeroDocumento,
            String nombre, String celular, String correo, String metodoPago) {
        return "{\"tipoDocumento\":\"" + tipoDocumento
                + "\",\"numeroDocumento\":\"" + numeroDocumento
                + "\",\"nombre\":\"" + nombre
                + "\",\"celular\":\"" + celular
                + "\",\"correo\":\"" + (correo == null ? "" : correo)
                + "\",\"metodoPago\":\"" + metodoPago + "\"}";
    }

    private void recordarDocumento(String tipo, String numero) {
        documentosContacto.add(new String[] {tipo, numero});
    }

    private long contarIngresos() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ingresos WHERE id_usuario = ?",
                Long.class, actor.getIdUsuario());
    }

    private long contarPagos() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pagos WHERE id_usuario = ?",
                Long.class, actor.getIdUsuario());
    }

    private long contarContactos(String tipo, String numero) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos_promocionales "
                        + "WHERE tipo_documento = ? AND numero_documento = ?",
                Long.class, tipo, numero);
    }

    private BigDecimal montoDelPagoUnico() {
        return jdbcTemplate.queryForObject(
                "SELECT monto FROM pagos WHERE id_usuario = ?",
                BigDecimal.class, actor.getIdUsuario());
    }

    private static String dniValido() {
        return "7" + String.format("%07d", SECUENCIA.incrementAndGet());
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String tokenCorto() {
        return token().substring(0, 10);
    }
}
