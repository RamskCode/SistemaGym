package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.AuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.AuditoriaAsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.EventoAsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-asistencia-service-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({AsistenciaLaboralService.class,
        AsistenciaLaboralServiceJpaTest.TestClockConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AsistenciaLaboralServiceJpaTest {

    private static final Instant INSTANTE_INICIAL =
            Instant.parse("2026-11-06T04:59:59.123456789Z");

    @Autowired
    private AsistenciaLaboralService service;

    @Autowired
    private AsistenciaLaboralRepository asistenciaRepository;

    @Autowired
    private EventoAsistenciaLaboralRepository eventoRepository;

    @Autowired
    private AuditoriaAsistenciaLaboralRepository auditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final Map<String, Usuario> usuarios = new HashMap<>();

    @BeforeEach
    void prepararDatosAislados() {
        clock.setInstant(INSTANTE_INICIAL);
        Map<String, Rol> roles = new HashMap<>();
        for (String nombre : new String[] {
                "ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA"}) {
            Rol rol = new Rol();
            rol.setNombre(nombre);
            roles.put(nombre, rolRepository.saveAndFlush(rol));
        }
        guardarUsuario("admin", roles.get("ADMINISTRADOR"));
        guardarUsuario("recepcionista", roles.get("RECEPCIONISTA"));
        guardarUsuario("recepcionista-otra", roles.get("RECEPCIONISTA"));
        guardarUsuario("entrenador", roles.get("ENTRENADOR"));
        guardarUsuario("nutricionista", roles.get("NUTRICIONISTA"));
    }

    @AfterEach
    void limpiarDatosAislados() {
        jdbcTemplate.update("delete from auditorias_asistencia_laboral");
        eventoRepository.deleteAllInBatch();
        asistenciaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();
        usuarios.clear();
    }

    @Test
    void permiteRecepcionistaPropiaYPersonalAutorizadoPeroRechazaLosDemas() {
        service.registrarEntrada(id("recepcionista"), id("recepcionista"));
        service.registrarEntrada(id("entrenador"), id("recepcionista"));
        service.registrarEntrada(id("nutricionista"), id("recepcionista"));

        assertThrows(AccessDeniedException.class,
                () -> service.registrarEntrada(id("entrenador"), id("entrenador")));
        assertThrows(AccessDeniedException.class,
                () -> service.registrarEntrada(id("nutricionista"), id("nutricionista")));
        assertThrows(AccessDeniedException.class,
                () -> service.registrarEntrada(id("entrenador"), id("admin")));
        assertThrows(AccessDeniedException.class,
                () -> service.registrarEntrada(id("admin"), id("recepcionista")));
        assertThrows(AccessDeniedException.class,
                () -> service.registrarEntrada(id("recepcionista-otra"), id("recepcionista")));
        assertEquals(3, asistenciaRepository.count());
    }

    @Test
    void listaSoloPersonalActivoSeleccionableEnOrdenEstable() {
        Usuario entrenadorInactivo = new Usuario();
        entrenadorInactivo.setRol(usuarios.get("entrenador").getRol());
        entrenadorInactivo.setNombres("Inactivo");
        entrenadorInactivo.setApellidos("Aaa");
        entrenadorInactivo.setCorreo("inactivo-asistencia@example.invalid");
        entrenadorInactivo.setContrasenaHash("hash-no-expuesto");
        entrenadorInactivo.setEstado("INACTIVO");
        usuarioRepository.saveAndFlush(entrenadorInactivo);

        var resultado = service.listarPersonalSeleccionable();

        assertEquals(2, resultado.size());
        assertEquals("entrenador", resultado.get(0).nombres());
        assertEquals("ENTRENADOR", resultado.get(0).rol());
        assertEquals("nutricionista", resultado.get(1).nombres());
        assertEquals("NUTRICIONISTA", resultado.get(1).rol());
        assertTrue(resultado.stream().noneMatch(persona ->
                persona.nombres().equals("admin")
                        || persona.nombres().startsWith("recepcionista")
                        || persona.nombres().equals("Inactivo")));
    }

    @Test
    void truncaMicrosegundosYCalculaFechaLaboralEnLima() {
        AsistenciaLaboral asistencia = service.registrarEntrada(
                id("entrenador"), id("recepcionista"));

        assertEquals(LocalDateTime.parse("2026-11-06T04:59:59.123456"),
                asistencia.getFechaEntradaUtc());
        assertEquals(LocalDate.of(2026, 11, 5), asistencia.getFechaLaboral());
        assertEquals(asistencia.getFechaEntradaUtc(),
                eventoRepository.findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        asistencia.getIdAsistencia()).getFirst().getFechaHoraUtc());
    }

    @Test
    void resuelveIdDelRegistradorDesdeCorreoAutenticadoEnElServidor() {
        AsistenciaLaboral asistencia = service.registrarEntradaAutenticada(
                id("entrenador"), "recepcionista-asistencia@example.invalid");

        assertEquals(id("recepcionista"),
                asistencia.getRegistradoEntrada().getIdUsuario());
        assertEquals(id("entrenador"), asistencia.getUsuario().getIdUsuario());
    }

    @Test
    void marcacionPropiaResuelveEmpleadoYRegistradorDesdeCorreoYRegistraEventos() {
        AsistenciaLaboral entrada = service.registrarMiEntradaAutenticada(
                "recepcionista-asistencia@example.invalid");

        assertEquals(id("recepcionista"), entrada.getUsuario().getIdUsuario());
        assertEquals(id("recepcionista"),
                entrada.getRegistradoEntrada().getIdUsuario());
        assertEquals(TipoEventoAsistenciaLaboral.ENTRADA,
                eventoRepository.findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        entrada.getIdAsistencia()).getFirst().getTipoEvento());

        clock.setInstant(INSTANTE_INICIAL.plusSeconds(3600));
        AsistenciaLaboral salida = service.registrarMiSalidaAutenticada(
                "recepcionista-asistencia@example.invalid");
        assertEquals(entrada.getIdAsistencia(), salida.getIdAsistencia());
        assertEquals(id("recepcionista"), salida.getRegistradoSalida().getIdUsuario());
        assertEquals(2, eventoRepository.findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                salida.getIdAsistencia()).size());
    }

    @Test
    void consultaPropiaRestringePorIdentidadYSoloRolesPermitidos() {
        service.registrarEntrada(id("entrenador"), id("recepcionista"));
        service.registrarEntrada(id("nutricionista"), id("recepcionista"));

        var historial = service.consultarMias(
                "entrenador-asistencia@example.invalid", 0, 20, null, null);

        assertEquals(1, historial.getTotalElements());
        assertEquals(id("entrenador"), historial.getContent().getFirst().idEmpleado());
        assertThrows(AccessDeniedException.class, () -> service.consultarMias(
                "admin-asistencia@example.invalid", 0, 20, null, null));
        assertThrows(AccessDeniedException.class, () -> service.consultarTodas(
                "recepcionista-asistencia@example.invalid", 0, 20, null, null, null));
    }

    @Test
    void consultaDetalleIncluyeEventosYAuditoriasOrdenadosSinExponerDatosSensibles() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        guardarEvento(asistencia, TipoEventoAsistenciaLaboral.ENTRADA,
                LocalDateTime.parse("2026-11-06T04:00:00"), "recepcionista");
        guardarEvento(asistencia, TipoEventoAsistenciaLaboral.SALIDA,
                LocalDateTime.parse("2026-11-06T05:00:00"), "recepcionista");

        service.corregirAsistencia(asistencia.getIdAsistencia(),
                Instant.parse("2026-11-06T03:59:00Z"), null,
                "Corrección uno", "admin-asistencia@example.invalid");
        service.corregirAsistencia(asistencia.getIdAsistencia(),
                Instant.parse("2026-11-06T03:58:00Z"), null,
                "Corrección dos", "admin-asistencia@example.invalid");
        service.anularAsistencia(asistencia.getIdAsistencia(), "Anulación final",
                "admin-asistencia@example.invalid");

        var historial = service.consultarHistorialAsistencia(
                asistencia.getIdAsistencia(), "admin-asistencia@example.invalid");

        assertEquals(asistencia.getIdAsistencia(), historial.idAsistencia());
        assertEquals(id("entrenador"), historial.empleado().idUsuario());
        assertEquals("entrenador-asistencia@example.invalid", historial.empleado().correo());
        assertEquals(EstadoAsistenciaLaboral.ANULADA, historial.estado());
        assertEquals("recepcionista-asistencia@example.invalid",
                historial.registradorEntrada().correo());
        assertEquals("recepcionista-asistencia@example.invalid",
                historial.registradorSalida().correo());

        assertEquals(2, historial.eventos().size());
        assertEquals(TipoEventoAsistenciaLaboral.ENTRADA,
                historial.eventos().get(0).tipoEvento());
        assertEquals(TipoEventoAsistenciaLaboral.SALIDA,
                historial.eventos().get(1).tipoEvento());
        assertTrue(historial.eventos().get(0).fechaHoraUtc()
                .isBefore(historial.eventos().get(1).fechaHoraUtc()));
        assertTrue(historial.eventos().get(0).idEvento()
                < historial.eventos().get(1).idEvento());

        assertEquals(3, historial.auditorias().size());
        assertEquals(TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION,
                historial.auditorias().get(0).tipoOperacion());
        assertEquals(TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION,
                historial.auditorias().get(1).tipoOperacion());
        assertEquals(TipoOperacionAuditoriaAsistenciaLaboral.ANULACION,
                historial.auditorias().get(2).tipoOperacion());
        assertTrue(historial.auditorias().get(0).idAuditoria()
                < historial.auditorias().get(1).idAuditoria());
        assertEquals("admin-asistencia@example.invalid",
                historial.auditorias().get(0).actor().correo());
        assertEquals("Corrección uno", historial.auditorias().get(0).motivo());
        assertEquals(LocalDateTime.parse("2026-11-06T04:00:00").toInstant(ZoneOffset.UTC),
                historial.auditorias().get(0).fechaEntradaAnteriorUtc());
        assertEquals(EstadoAsistenciaLaboral.ACTIVA,
                historial.auditorias().get(2).estadoAnterior());
        assertEquals(EstadoAsistenciaLaboral.ANULADA,
                historial.auditorias().get(2).estadoNuevo());
        assertFalse(historial.toString().contains("hash-de-prueba"));
    }

    @Test
    void historialRequiereAdministradorYDevuelveNoEncontradoParaIdAusente() {
        assertThrows(AccessDeniedException.class, () -> service.consultarHistorialAsistencia(
                999L, "entrenador-asistencia@example.invalid"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.consultarHistorialAsistencia(
                        999L, "admin-asistencia@example.invalid"));
    }

    @Test
    void reporteCuentaEstadosJornadasYHorasSoloDeActivasCerradas() {
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        crearAsistenciaAbierta("entrenador",
                LocalDateTime.parse("2026-11-07T04:00:00"));
        AsistenciaLaboral anuladaCerrada = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-08T04:00:00"),
                LocalDateTime.parse("2026-11-08T07:00:00"));
        anuladaCerrada.setEstado(EstadoAsistenciaLaboral.ANULADA);
        asistenciaRepository.saveAndFlush(anuladaCerrada);
        AsistenciaLaboral anuladaAbierta = crearAsistenciaAbierta("entrenador",
                LocalDateTime.parse("2026-11-09T04:00:00"));
        anuladaAbierta.setEstado(EstadoAsistenciaLaboral.ANULADA);
        asistenciaRepository.saveAndFlush(anuladaAbierta);

        var reporte = service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                "admin-asistencia@example.invalid");

        assertEquals(2, reporte.cantidadActivas());
        assertEquals(2, reporte.cantidadAnuladas());
        assertEquals(1, reporte.jornadasCerradas());
        assertEquals(1, reporte.jornadasAbiertas());
        assertEquals(new BigDecimal("1.00"), reporte.totalHorasRegistradas());
    }

    @Test
    void reporteIncluyeAmbosLimitesDeFechaLaboralYExcluyeDiasExternos() {
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-01T05:00:00"),
                LocalDateTime.parse("2026-11-01T06:00:00"));
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-30T05:00:00"),
                LocalDateTime.parse("2026-11-30T06:00:00"));
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-10-31T05:00:00"),
                LocalDateTime.parse("2026-10-31T06:00:00"));
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-12-01T05:00:00"),
                LocalDateTime.parse("2026-12-01T06:00:00"));

        var reporte = service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                "admin-asistencia@example.invalid");

        assertEquals(2, reporte.cantidadActivas());
        assertEquals(2, reporte.jornadasCerradas());
        assertEquals(new BigDecimal("2.00"), reporte.totalHorasRegistradas());
    }

    @Test
    void reporteUsaTiemposCorregidosVigentesSinSumarUnaJornadaAdicional() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        service.corregirAsistencia(asistencia.getIdAsistencia(),
                Instant.parse("2026-11-06T03:30:00Z"), null,
                "Corrección de entrada", "admin-asistencia@example.invalid");

        var reporte = service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 5),
                "admin-asistencia@example.invalid");

        assertEquals(1, reporte.cantidadActivas());
        assertEquals(1, reporte.jornadasCerradas());
        assertEquals(new BigDecimal("1.50"), reporte.totalHorasRegistradas());
    }

    @Test
    void reporteRedondeaUnaSolaVezElTotalDeMicrosegundosAcentimasDeHora() {
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00.000000"),
                LocalDateTime.parse("2026-11-06T04:01:00.000000"));
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-07T04:00:00.000000"),
                LocalDateTime.parse("2026-11-07T04:01:00.000000"));

        var reporte = service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                "admin-asistencia@example.invalid");

        assertEquals(new BigDecimal("0.03"), reporte.totalHorasRegistradas());
    }

    @Test
    void reporteValidaIdentidadParametrosExistenciaYLimiteInclusivoDeDoceMeses() {
        assertThrows(AccessDeniedException.class, () -> service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6),
                "entrenador-asistencia@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.consultarReporteEmpleado(
                0L, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6),
                "admin-asistencia@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.consultarReporteEmpleado(
                id("entrenador"), null, LocalDate.of(2026, 11, 6),
                "admin-asistencia@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 6),
                "admin-asistencia@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 6), LocalDate.of(2027, 11, 7),
                "admin-asistencia@example.invalid"));
        assertThrows(RecursoNoEncontradoException.class, () -> service.consultarReporteEmpleado(
                999L, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6),
                "admin-asistencia@example.invalid"));

        var reporteVacio = service.consultarReporteEmpleado(
                id("entrenador"), LocalDate.of(2026, 11, 6), LocalDate.of(2027, 11, 6),
                "admin-asistencia@example.invalid");
        assertEquals(0, reporteVacio.cantidadActivas());
        assertEquals(0, reporteVacio.cantidadAnuladas());
        assertEquals(0, reporteVacio.jornadasCerradas());
        assertEquals(0, reporteVacio.jornadasAbiertas());
        assertEquals(new BigDecimal("0.00"), reporteVacio.totalHorasRegistradas());
    }

    @Test
    void admiteTurnoNocturnoYRegistraAmbosEventosAtomicamente() {
        AsistenciaLaboral entrada = service.registrarEntrada(
                id("entrenador"), id("recepcionista"));
        clock.setInstant(Instant.parse("2026-11-06T05:01:00.654321987Z"));

        AsistenciaLaboral salida = service.registrarSalida(
                id("entrenador"), id("recepcionista"));
        var eventos = eventoRepository
                .findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        entrada.getIdAsistencia());

        assertEquals(LocalDate.of(2026, 11, 5), salida.getFechaLaboral());
        assertEquals(LocalDateTime.parse("2026-11-06T05:01:00.654321"),
                salida.getFechaSalidaUtc());
        assertEquals(2, eventos.size());
        assertEquals(TipoEventoAsistenciaLaboral.ENTRADA, eventos.get(0).getTipoEvento());
        assertEquals(TipoEventoAsistenciaLaboral.SALIDA, eventos.get(1).getTipoEvento());
        assertEquals(id("recepcionista"),
                eventos.get(1).getUsuarioRegistrador().getIdUsuario());
    }

    @Test
    void rechazaEntradaAbiertaDuplicadaYSalidaSinEntrada() {
        assertThrows(ConflictoException.class,
                () -> service.registrarSalida(id("entrenador"), id("recepcionista")));
        service.registrarEntrada(id("entrenador"), id("recepcionista"));
        assertThrows(ConflictoException.class,
                () -> service.registrarEntrada(id("entrenador"), id("recepcionista")));
    }

    @Test
    void rechazaSolapamientoPeroPermiteIntervalosAdyacentes() {
        crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:59:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        clock.setInstant(Instant.parse("2026-11-06T05:00:00Z"));
        service.registrarEntrada(id("entrenador"), id("recepcionista"));

        crearAsistenciaCerrada("nutricionista",
                LocalDateTime.parse("2026-11-06T05:00:30"),
                LocalDateTime.parse("2026-11-06T05:02:00"));
        assertThrows(ConflictoException.class,
                () -> service.registrarEntrada(id("nutricionista"), id("recepcionista")));
    }

    @Test
    void correccionAdministrativaAuditaSnapshotsYRecalculaFechaLaboral() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T05:00:00"),
                LocalDateTime.parse("2026-11-06T06:00:00"));
        int eventosAntes = eventoRepository.findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                asistencia.getIdAsistencia()).size();

        AsistenciaLaboral corregida = service.corregirAsistencia(
                asistencia.getIdAsistencia(), Instant.parse("2026-11-06T04:59:00.987654321Z"),
                null, "Corrección autorizada", "admin-asistencia@example.invalid");

        assertEquals(LocalDateTime.parse("2026-11-06T04:59:00.987654"),
                corregida.getFechaEntradaUtc());
        assertEquals(LocalDate.of(2026, 11, 5), corregida.getFechaLaboral());
        assertEquals(eventosAntes, eventoRepository
                .findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        asistencia.getIdAsistencia()).size());
        var auditoria = auditoriaRepository
                .existsByAsistencia_IdAsistenciaAndTipoOperacion(
                        asistencia.getIdAsistencia(), TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION);
        assertTrue(auditoria);
        assertEquals(id("admin"), jdbcTemplate.queryForObject(
                "select id_usuario_actor from auditorias_asistencia_laboral "
                        + "where id_asistencia = ?",
                Long.class, asistencia.getIdAsistencia()));
    }

    @Test
    void administradorAnulaSinCambiarEventosYQuedaAuditado() {
        AsistenciaLaboral asistencia = service.registrarEntrada(
                id("entrenador"), id("recepcionista"));
        int eventosAntes = eventoRepository
                .findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        asistencia.getIdAsistencia()).size();

        AsistenciaLaboral anulada = service.anularAsistencia(asistencia.getIdAsistencia(),
                "Registro duplicado", "admin-asistencia@example.invalid");

        assertEquals(EstadoAsistenciaLaboral.ANULADA, anulada.getEstado());
        assertEquals(eventosAntes, eventoRepository
                .findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        asistencia.getIdAsistencia()).size());
        assertTrue(auditoriaRepository
                .existsByAsistencia_IdAsistenciaAndTipoOperacion(
                        asistencia.getIdAsistencia(), TipoOperacionAuditoriaAsistenciaLaboral.ANULACION));
        assertEquals(id("admin"), jdbcTemplate.queryForObject(
                "select id_usuario_actor from auditorias_asistencia_laboral "
                        + "where id_asistencia = ?",
                Long.class, asistencia.getIdAsistencia()));
        assertThrows(ConflictoException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "Reintento", "admin-asistencia@example.invalid"));
    }

    @Test
    void rechazaCorreccionYAnulacionDeAsistenciaParaRecepcionista() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        Instant entrada = asistencia.getFechaEntradaUtc().toInstant(ZoneOffset.UTC);

        assertThrows(AccessDeniedException.class, () -> service.corregirAsistencia(
                asistencia.getIdAsistencia(), entrada, null, "No autorizado",
                "recepcionista-asistencia@example.invalid"));
        assertThrows(ConflictoException.class, () -> service.corregirAsistencia(
                asistencia.getIdAsistencia(), entrada, null, "Sin cambios",
                "admin-asistencia@example.invalid"));
        assertThrows(AccessDeniedException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "Anulación no autorizada",
                "recepcionista-asistencia@example.invalid"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from auditorias_asistencia_laboral", Integer.class));
    }

    @Test
    void soloAdministradorPuedeAnularTrasCorreccion() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        service.corregirAsistencia(asistencia.getIdAsistencia(),
                Instant.parse("2026-11-06T04:01:00Z"), null,
                "Corrección administrativa", "admin-asistencia@example.invalid");

        assertThrows(AccessDeniedException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "Anulación no permitida",
                "recepcionista-asistencia@example.invalid"));
        assertThrows(AccessDeniedException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "Anulación no permitida",
                "entrenador-asistencia@example.invalid"));
        assertThrows(AccessDeniedException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "Anulación no permitida",
                "nutricionista-asistencia@example.invalid"));
        assertEquals(EstadoAsistenciaLaboral.ACTIVA,
                asistenciaRepository.findById(asistencia.getIdAsistencia())
                        .orElseThrow().getEstado());
    }

    @Test
    void rechazaMotivoVacioOMayorAQuinientosCaracteres() {
        AsistenciaLaboral asistencia = service.registrarEntrada(
                id("entrenador"), id("recepcionista"));
        assertThrows(IllegalArgumentException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "  ", "admin-asistencia@example.invalid"));
        assertThrows(IllegalArgumentException.class, () -> service.anularAsistencia(
                asistencia.getIdAsistencia(), "x".repeat(501),
                "admin-asistencia@example.invalid"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from auditorias_asistencia_laboral", Integer.class));
    }

    @Test
    void rechazaCerrarAbiertaReabrirCerradaYIntervalosSuperpuestos() {
        AsistenciaLaboral abierta = service.registrarEntrada(id("entrenador"), id("recepcionista"));
        assertThrows(ConflictoException.class, () -> service.corregirAsistencia(
                abierta.getIdAsistencia(), null, clock.instant(), "No cerrar",
                "admin-asistencia@example.invalid"));

        AsistenciaLaboral cerrada = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        assertThrows(ConflictoException.class, () -> service.corregirAsistencia(
                cerrada.getIdAsistencia(), null, null, "No reabrir",
                "admin-asistencia@example.invalid"));
        assertThrows(ConflictoException.class, () -> service.corregirAsistencia(
                cerrada.getIdAsistencia(), Instant.parse("2026-11-06T04:30:00Z"), null,
                "Solapamiento", "admin-asistencia@example.invalid"));
    }

    @Test
    void serializaCorreccionYAnulacionConcurrentesDeLaMismaAsistencia() throws Exception {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        CyclicBarrier inicioSimultaneo = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> correccion = executor.submit(() -> intentarOperacionConcurrente(
                    inicioSimultaneo, () -> service.corregirAsistencia(
                            asistencia.getIdAsistencia(),
                            Instant.parse("2026-11-06T03:59:00Z"), null,
                            "Corrección concurrente", "admin-asistencia@example.invalid")));
            Future<Boolean> anulacion = executor.submit(() -> intentarOperacionConcurrente(
                    inicioSimultaneo, () -> service.anularAsistencia(
                            asistencia.getIdAsistencia(), "Anulación concurrente",
                            "admin-asistencia@example.invalid")));

            correccion.get(15, TimeUnit.SECONDS);
            anulacion.get(15, TimeUnit.SECONDS);
            AsistenciaLaboral finalizada = asistenciaRepository
                    .findById(asistencia.getIdAsistencia()).orElseThrow();
            assertEquals(EstadoAsistenciaLaboral.ANULADA, finalizada.getEstado());
            assertTrue(auditoriaRepository.existsByAsistencia_IdAsistenciaAndTipoOperacion(
                    asistencia.getIdAsistencia(), TipoOperacionAuditoriaAsistenciaLaboral.ANULACION));
            int cantidadAuditorias = jdbcTemplate.queryForObject(
                    "select count(*) from auditorias_asistencia_laboral", Integer.class);
            assertTrue(cantidadAuditorias == 1 || cantidadAuditorias == 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void correccionYAuditoriaSeReviertenEnLaMismaTransaccion() {
        AsistenciaLaboral asistencia = crearAsistenciaCerrada("entrenador",
                LocalDateTime.parse("2026-11-06T04:00:00"),
                LocalDateTime.parse("2026-11-06T05:00:00"));
        LocalDateTime entradaOriginal = asistencia.getFechaEntradaUtc();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.corregirAsistencia(asistencia.getIdAsistencia(),
                    Instant.parse("2026-11-06T03:59:00Z"), null,
                    "Ajuste y rollback", "admin-asistencia@example.invalid");
            throw new IllegalStateException("Forzar rollback de corrección y auditoría");
        }));

        AsistenciaLaboral persistida = asistenciaRepository
                .findById(asistencia.getIdAsistencia()).orElseThrow();
        assertEquals(entradaOriginal, persistida.getFechaEntradaUtc());
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from auditorias_asistencia_laboral", Integer.class));
    }

    private boolean intentarOperacionConcurrente(CyclicBarrier barrera, Runnable operacion)
            throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        try {
            operacion.run();
            return true;
        } catch (ConflictoException ex) {
            return false;
        }
    }

    @Test
    void asistenciaYEventoSeReviertenComoUnaUnidadTransaccional() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            service.registrarEntrada(id("entrenador"), id("recepcionista"));
            throw new IllegalStateException("Forzar rollback para la prueba");
        }));

        assertEquals(0, asistenciaRepository.count());
        assertEquals(0, eventoRepository.count());
    }

    @Test
    void serializaEntradasConcurrentesDelMismoEmpleado() throws Exception {
        CyclicBarrier inicioSimultaneo = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> primera = executor.submit(() -> intentarEntradaConcurrente(
                    inicioSimultaneo));
            Future<Boolean> segunda = executor.submit(() -> intentarEntradaConcurrente(
                    inicioSimultaneo));

            int exitosas = (primera.get(15, TimeUnit.SECONDS) ? 1 : 0)
                    + (segunda.get(15, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, exitosas);
            assertEquals(1, asistenciaRepository.count());
            assertEquals(1, eventoRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean intentarEntradaConcurrente(CyclicBarrier barrera) throws Exception {
        barrera.await(5, TimeUnit.SECONDS);
        try {
            service.registrarEntrada(id("entrenador"), id("recepcionista"));
            return true;
        } catch (ConflictoException ex) {
            return false;
        }
    }

    private AsistenciaLaboral crearAsistenciaCerrada(String empleado,
            LocalDateTime entradaUtc, LocalDateTime salidaUtc) {
        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(usuarios.get(empleado));
        asistencia.setRegistradoEntrada(usuarios.get("recepcionista"));
        asistencia.setFechaEntradaUtc(entradaUtc.toInstant(ZoneOffset.UTC));
        asistencia.setFechaSalidaUtc(salidaUtc.toInstant(ZoneOffset.UTC));
        asistencia.setRegistradoSalida(usuarios.get("recepcionista"));
        return asistenciaRepository.saveAndFlush(asistencia);
    }

    private AsistenciaLaboral crearAsistenciaAbierta(String empleado,
            LocalDateTime entradaUtc) {
        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(usuarios.get(empleado));
        asistencia.setRegistradoEntrada(usuarios.get("recepcionista"));
        asistencia.setFechaEntradaUtc(entradaUtc.toInstant(ZoneOffset.UTC));
        return asistenciaRepository.saveAndFlush(asistencia);
    }

    private void guardarEvento(AsistenciaLaboral asistencia, TipoEventoAsistenciaLaboral tipo,
            LocalDateTime fechaHoraUtc, String registrador) {
        EventoAsistenciaLaboral evento = new EventoAsistenciaLaboral();
        evento.setAsistencia(asistencia);
        evento.setTipoEvento(tipo);
        evento.setFechaHoraUtc(fechaHoraUtc.toInstant(ZoneOffset.UTC));
        evento.setUsuarioRegistrador(usuarios.get(registrador));
        eventoRepository.saveAndFlush(evento);
    }

    private void guardarUsuario(String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombres(nombre);
        usuario.setApellidos("Prueba");
        usuario.setCorreo(nombre + "-asistencia@example.invalid");
        usuario.setContrasenaHash("hash-de-prueba");
        usuarios.put(nombre, usuarioRepository.saveAndFlush(usuario));
    }

    private Long id(String nombre) {
        return usuarios.get(nombre).getIdUsuario();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfiguration {

        @Bean(name = "reloj")
        MutableClock asistenciaLaboralTestClock() {
            return new MutableClock(INSTANTE_INICIAL);
        }
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instante;

        MutableClock(Instant inicial) {
            instante = new AtomicReference<>(inicial);
        }

        void setInstant(Instant nuevoInstante) {
            instante.set(nuevoInstante);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("El reloj de prueba es UTC.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instante.get();
        }
    }
}
