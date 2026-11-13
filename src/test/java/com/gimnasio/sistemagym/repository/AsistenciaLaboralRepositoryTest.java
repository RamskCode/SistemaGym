package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.AuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.gimnasio.sistemagym.repository.AuditoriaAsistenciaLaboralRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-asistencias-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class AsistenciaLaboralRepositoryTest {

    @Autowired
    private AsistenciaLaboralRepository asistenciaRepository;

    @Autowired
    private AuditoriaAsistenciaLaboralRepository auditoriaRepository;

    @Autowired
    private EventoAsistenciaLaboralRepository eventoRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario empleado;
    private Usuario registrador;

    @BeforeEach
    void prepararPersonalDePrueba() {
        Rol rol = new Rol();
        rol.setNombre("RECEPCIONISTA");
        rol = rolRepository.saveAndFlush(rol);
        empleado = guardarUsuario(rol, "empleado-asistencia@example.invalid");
        registrador = guardarUsuario(rol, "registrador-asistencia@example.invalid");
    }

    @Test
    void persisteInstantesUtcFechaLaboralYEvento() {
        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(empleado);
        asistencia.setRegistradoEntrada(registrador);
        asistencia.setFechaEntradaUtc(Instant.parse("2026-11-05T04:30:00.123456Z"));
        asistencia = asistenciaRepository.saveAndFlush(asistencia);

        EventoAsistenciaLaboral evento = new EventoAsistenciaLaboral();
        evento.setAsistencia(asistencia);
        evento.setTipoEvento(TipoEventoAsistenciaLaboral.ENTRADA);
        evento.setFechaHoraUtc(Instant.parse("2026-11-05T04:30:00.123456Z"));
        evento.setUsuarioRegistrador(registrador);
        eventoRepository.saveAndFlush(evento);

        AsistenciaLaboral persistida = asistenciaRepository.findById(
                asistencia.getIdAsistencia()).orElseThrow();
        assertEquals(Instant.parse("2026-11-05T04:30:00.123456Z"),
                persistida.getFechaEntradaUtc().toInstant(java.time.ZoneOffset.UTC));
        assertEquals(LocalDate.of(2026, 11, 4), persistida.getFechaLaboral());
        assertNull(persistida.getFechaSalidaUtc());
        assertEquals(TipoEventoAsistenciaLaboral.ENTRADA,
                eventoRepository.findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
                        asistencia.getIdAsistencia()).getFirst().getTipoEvento());
    }

    @Test
    void rechazaSalidaIgualOAnteriorALaEntrada() {
        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(empleado);
        asistencia.setRegistradoEntrada(registrador);
        asistencia.setFechaEntradaUtc(Instant.parse("2026-11-05T04:30:00Z"));
        asistencia.setFechaSalidaUtc(Instant.parse("2026-11-05T04:30:00Z"));
        asistencia.setRegistradoSalida(registrador);

        assertThrows(DataIntegrityViolationException.class,
                () -> asistenciaRepository.saveAndFlush(asistencia));
    }

    @Test
    void paginaPorFechaLaboralConOrdenEstableYFiltrosInclusivos() {
        Instant entrada = Instant.parse("2026-11-05T04:30:00Z");
        guardarAsistencia(entrada);
        AsistenciaLaboral segunda = guardarAsistencia(entrada);

        var pageable = PageRequest.of(0, 10,
                Sort.by(Sort.Order.desc("fechaEntradaUtc"),
                        Sort.Order.desc("idAsistencia")));
        var pagina = asistenciaRepository.consultarHistorial(
                empleado.getIdUsuario(), LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 11, 4), null, pageable);
        AsistenciaLaboral anulada = guardarAsistencia(entrada);
        anulada.setEstado(EstadoAsistenciaLaboral.ANULADA);
        asistenciaRepository.saveAndFlush(anulada);

        assertEquals(2, pagina.getTotalElements());
        assertEquals(segunda.getIdAsistencia(), pagina.getContent().getFirst().getIdAsistencia());
        assertEquals(empleado.getIdUsuario(), pagina.getContent().getFirst().getIdEmpleado());
        assertEquals("Usuario", pagina.getContent().getFirst().getNombresEmpleado());
        assertEquals("Prueba", pagina.getContent().getFirst().getApellidosEmpleado());
        assertEquals("RECEPCIONISTA", pagina.getContent().getFirst().getRolEmpleado());
        assertEquals(0, asistenciaRepository.consultarHistorial(
                empleado.getIdUsuario(), LocalDate.of(2026, 11, 5),
                LocalDate.of(2026, 11, 5), null, pageable).getTotalElements());
        assertEquals(3, asistenciaRepository.consultarHistorial(
                null, null, null, null, pageable).getTotalElements());
        assertEquals(1, asistenciaRepository.consultarHistorial(
                empleado.getIdUsuario(), null, null,
                EstadoAsistenciaLaboral.ANULADA, pageable).getTotalElements());
    }

    @Test
    void empleadosConHistorialIncluyeInactivosYSoloUsuariosConAsistencias() {
        empleado.setEstado("INACTIVO");
        usuarioRepository.saveAndFlush(empleado);
        guardarAsistencia(Instant.parse("2026-11-05T04:30:00Z"));

        var empleados = asistenciaRepository.listarEmpleadosConHistorial();

        assertEquals(1, empleados.size());
        assertEquals(empleado.getIdUsuario(), empleados.getFirst().getIdEmpleado());
        assertEquals("Usuario", empleados.getFirst().getNombres());
        assertEquals("RECEPCIONISTA", empleados.getFirst().getRol());
    }

    @Test
    void guardaAuditoriaInmutableConInstanteUtcYOperacion() {
        AsistenciaLaboral asistencia = guardarAsistencia(
                Instant.parse("2026-11-05T04:30:00Z"));
        var fechaAnterior = asistencia.getFechaEntradaUtc();
        var fechaNueva = fechaAnterior.plusMinutes(5);

        AuditoriaAsistenciaLaboral auditoria = auditoriaRepository.save(
                AuditoriaAsistenciaLaboral.crear(
                        asistencia, registrador, Instant.parse("2026-11-05T05:00:00Z"),
                        TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION, "Ajuste autorizado",
                        EstadoAsistenciaLaboral.ACTIVA, EstadoAsistenciaLaboral.ACTIVA,
                        fechaAnterior, null, fechaNueva, null));

        assertEquals(TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION,
                auditoria.getTipoOperacion());
        assertEquals(LocalDateTime.parse("2026-11-05T05:00:00"),
                auditoria.getFechaHoraUtc());
        assertEquals(fechaAnterior, auditoria.getFechaEntradaAnteriorUtc());
        assertEquals(fechaNueva, auditoria.getFechaEntradaNuevaUtc());
        assertTrue(auditoriaRepository
                .existsByAsistencia_IdAsistenciaAndTipoOperacion(
                        asistencia.getIdAsistencia(),
                        TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION));
    }

    @Test
    void lasConsultasDeSolapamientoIgnoranAsistenciasAnuladas() {
        AsistenciaLaboral anulada = guardarAsistencia(Instant.parse("2026-11-05T04:00:00Z"));
        anulada.setEstado(EstadoAsistenciaLaboral.ANULADA);
        asistenciaRepository.saveAndFlush(anulada);
        AsistenciaLaboral activa = guardarAsistencia(Instant.parse("2026-11-05T04:30:00Z"));

        var solapamientos = asistenciaRepository.findSolapamientosSesionAbierta(
                empleado.getIdUsuario(), LocalDateTime.parse("2026-11-05T04:45:00"),
                EstadoAsistenciaLaboral.ACTIVA, null);

        assertEquals(1, solapamientos.size());
        assertEquals(activa.getIdAsistencia(), solapamientos.getFirst().getIdAsistencia());
    }

    private AsistenciaLaboral guardarAsistencia(Instant entrada) {
        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(empleado);
        asistencia.setRegistradoEntrada(registrador);
        asistencia.setFechaEntradaUtc(entrada);
        return asistenciaRepository.saveAndFlush(asistencia);
    }

    private Usuario guardarUsuario(Rol rol, String correo) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombres("Usuario");
        usuario.setApellidos("Prueba");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("hash-de-prueba");
        return usuarioRepository.saveAndFlush(usuario);
    }
}
