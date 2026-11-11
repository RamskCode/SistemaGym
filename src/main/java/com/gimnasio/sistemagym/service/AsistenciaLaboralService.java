package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.AuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralHistorialRespuesta;
import com.gimnasio.sistemagym.dto.ReporteAsistenciaLaboralEmpleadoRespuesta;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralRespuesta;
import com.gimnasio.sistemagym.dto.PersonalSeleccionableRespuesta;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.PersonalSeleccionableConsulta;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralDetalleConsulta;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralReporteConsulta;
import com.gimnasio.sistemagym.repository.AuditoriaAsistenciaHistorialConsulta;
import com.gimnasio.sistemagym.repository.AuditoriaAsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.EventoAsistenciaHistorialConsulta;
import com.gimnasio.sistemagym.repository.EventoAsistenciaLaboralRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AsistenciaLaboralService {

    private static final String ROL_RECEPCIONISTA = "RECEPCIONISTA";
    private static final String ROL_ENTRENADOR = "ENTRENADOR";
    private static final String ROL_NUTRICIONISTA = "NUTRICIONISTA";
    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final Set<Integer> TAMANOS_PAGINA_PERMITIDOS = Set.of(10, 20, 50, 100);
    private static final BigDecimal MICROS_POR_SEGUNDO = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal MICROS_POR_HORA = BigDecimal.valueOf(3_600_000_000L);

    private final UsuarioRepository usuarioRepository;
    private final AsistenciaLaboralRepository asistenciaRepository;
    private final EventoAsistenciaLaboralRepository eventoRepository;
    private final AuditoriaAsistenciaLaboralRepository auditoriaRepository;
    private final Clock clock;

    public AsistenciaLaboralService(
            UsuarioRepository usuarioRepository,
            AsistenciaLaboralRepository asistenciaRepository,
            EventoAsistenciaLaboralRepository eventoRepository,
            AuditoriaAsistenciaLaboralRepository auditoriaRepository,
            @Qualifier("reloj") Clock reloj) {
        this.usuarioRepository = usuarioRepository;
        this.asistenciaRepository = asistenciaRepository;
        this.eventoRepository = eventoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.clock = reloj;
    }

    @Transactional
    public AsistenciaLaboral registrarEntrada(Long idEmpleado, Long idRegistrador) {
        validarIds(idEmpleado, idRegistrador);
        Usuario registrador = obtenerRegistradorRecepcionista(idRegistrador);
        Usuario empleado = obtenerEmpleadoBloqueado(idEmpleado);
        validarEmpleadoPermitido(empleado, registrador);

        Instant instante = ahoraUtcConPrecisionMicrosegundos();
        LocalDateTime entradaUtc = LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
        if (!asistenciaRepository.findSolapamientosSesionAbierta(
                idEmpleado, entradaUtc, EstadoAsistenciaLaboral.ACTIVA, null)
                .isEmpty()) {
            throw new ConflictoException(
                    "La entrada se solapa con otra asistencia del empleado.");
        }

        AsistenciaLaboral asistencia = new AsistenciaLaboral();
        asistencia.setUsuario(empleado);
        asistencia.setRegistradoEntrada(registrador);
        asistencia.setFechaEntradaUtc(instante);

        try {
            asistencia = asistenciaRepository.saveAndFlush(asistencia);
            guardarEvento(asistencia, TipoEventoAsistenciaLaboral.ENTRADA,
                    instante, registrador);
            return asistencia;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo registrar la entrada por conflicto con otra asistencia.");
        }
    }

    @Transactional(readOnly = true)
    public List<PersonalSeleccionableRespuesta> listarPersonalSeleccionable() {
        return usuarioRepository.listarPersonalSeleccionable().stream()
                .map(this::mapearPersonalSeleccionable)
                .toList();
    }

    @Transactional
    public AsistenciaLaboral registrarMiEntradaAutenticada(String correoAutenticado) {
        Long idUsuario = obtenerIdRegistradorAutenticado(correoAutenticado);
        return registrarEntrada(idUsuario, idUsuario);
    }

    @Transactional
    public AsistenciaLaboral registrarMiSalidaAutenticada(String correoAutenticado) {
        Long idUsuario = obtenerIdRegistradorAutenticado(correoAutenticado);
        return registrarSalida(idUsuario, idUsuario);
    }

    private PersonalSeleccionableRespuesta mapearPersonalSeleccionable(
            PersonalSeleccionableConsulta consulta) {
        return new PersonalSeleccionableRespuesta(
                consulta.getIdEmpleado(), consulta.getNombres(),
                consulta.getApellidos(), consulta.getRol());
    }

    /** Resolves the authenticated JWT subject server-side, then reuses the ID-based operation. */
    @Transactional
    public AsistenciaLaboral registrarEntradaAutenticada(
            Long idEmpleado, String correoRegistradorAutenticado) {
        Long idRegistrador = obtenerIdRegistradorAutenticado(correoRegistradorAutenticado);
        return registrarEntrada(idEmpleado, idRegistrador);
    }

    @Transactional
    public AsistenciaLaboral registrarSalida(Long idEmpleado, Long idRegistrador) {
        validarIds(idEmpleado, idRegistrador);
        Usuario registrador = obtenerRegistradorRecepcionista(idRegistrador);
        Usuario empleado = obtenerEmpleadoBloqueado(idEmpleado);
        validarEmpleadoPermitido(empleado, registrador);

        AsistenciaLaboral asistencia = asistenciaRepository
                .findFirstByUsuario_IdUsuarioAndFechaSalidaUtcIsNullAndEstadoOrderByFechaEntradaUtcDesc(
                        idEmpleado, EstadoAsistenciaLaboral.ACTIVA)
                .orElseThrow(() -> new ConflictoException(
                        "El empleado no tiene una entrada abierta para registrar salida."));

        Instant instante = ahoraUtcConPrecisionMicrosegundos();
        LocalDateTime salidaUtc = LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
        LocalDateTime entradaUtc = asistencia.getFechaEntradaUtc();
        if (!salidaUtc.isAfter(entradaUtc)) {
            throw new ConflictoException(
                    "La salida debe ser posterior a la entrada.");
        }
        if (!asistenciaRepository.findSolapamientos(
                idEmpleado, entradaUtc, salidaUtc, asistencia.getIdAsistencia(),
                EstadoAsistenciaLaboral.ACTIVA).isEmpty()) {
            throw new ConflictoException(
                    "El intervalo de asistencia se solapa con otro registro.");
        }

        asistencia.setFechaSalidaUtc(instante);
        asistencia.setRegistradoSalida(registrador);
        try {
            asistencia = asistenciaRepository.saveAndFlush(asistencia);
            guardarEvento(asistencia, TipoEventoAsistenciaLaboral.SALIDA,
                    instante, registrador);
            return asistencia;
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo registrar la salida por conflicto con otra asistencia.");
        }
    }

    /** Resolves the authenticated JWT subject server-side, then reuses the ID-based operation. */
    @Transactional
    public AsistenciaLaboral registrarSalidaAutenticada(
            Long idEmpleado, String correoRegistradorAutenticado) {
        Long idRegistrador = obtenerIdRegistradorAutenticado(correoRegistradorAutenticado);
        return registrarSalida(idEmpleado, idRegistrador);
    }

    /**
     * Corrects active attendance times without creating or changing ENTRADA/SALIDA events.
     * Null time arguments mean "keep the current value"; clearing an exit is not supported.
     */
    @Transactional
    public AsistenciaLaboral corregirAsistencia(
            Long idAsistencia,
            Instant nuevaEntradaUtc,
            Instant nuevaSalidaUtc,
            String motivo,
            String correoActorAutenticado) {
        Usuario actor = obtenerUsuarioAutenticado(correoActorAutenticado);
        if (!ROL_ADMINISTRADOR.equals(nombreRol(actor))) {
            throw new AccessDeniedException("Solo un administrador puede corregir asistencias.");
        }

        AsistenciaLaboral asistencia = obtenerAsistenciaBloqueada(idAsistencia);
        validarAsistenciaActiva(asistencia);
        String motivoValidado = validarMotivo(motivo);

        LocalDateTime entradaAnterior = asistencia.getFechaEntradaUtc();
        LocalDateTime salidaAnterior = asistencia.getFechaSalidaUtc();
        LocalDateTime entradaNueva = nuevaEntradaUtc == null
                ? entradaAnterior : aUtcMicrosegundos(nuevaEntradaUtc);
        LocalDateTime salidaNueva = nuevaSalidaUtc == null
                ? salidaAnterior : aUtcMicrosegundos(nuevaSalidaUtc);

        if (salidaAnterior == null && salidaNueva != null) {
            throw new ConflictoException(
                    "La corrección no puede cerrar una asistencia abierta.");
        }
        if (entradaNueva.equals(entradaAnterior) && Objects.equals(salidaNueva, salidaAnterior)) {
            throw new ConflictoException("La corrección no modifica la asistencia.");
        }
        if (salidaNueva != null && !salidaNueva.isAfter(entradaNueva)) {
            throw new ConflictoException("La salida debe ser posterior a la entrada.");
        }

        validarSinSolapamientos(asistencia, entradaNueva, salidaNueva);
        asistencia.setFechaEntradaUtc(entradaNueva.toInstant(ZoneOffset.UTC));
        asistencia.setFechaSalidaUtc(salidaNueva == null
                ? null : salidaNueva.toInstant(ZoneOffset.UTC));
        asistenciaRepository.saveAndFlush(asistencia);
        guardarAuditoria(asistencia, actor, TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION,
                motivoValidado, EstadoAsistenciaLaboral.ACTIVA,
                EstadoAsistenciaLaboral.ACTIVA, entradaAnterior, salidaAnterior,
                entradaNueva, salidaNueva);
        return asistencia;
    }

    /** Annuls an active attendance; original attendance events remain untouched. */
    @Transactional
    public AsistenciaLaboral anularAsistencia(
            Long idAsistencia, String motivo, String correoActorAutenticado) {
        Usuario actor = obtenerUsuarioAutenticado(correoActorAutenticado);
        if (!ROL_ADMINISTRADOR.equals(nombreRol(actor))) {
            throw new AccessDeniedException(
                    "Solo un administrador puede anular asistencias laborales.");
        }

        AsistenciaLaboral asistencia = obtenerAsistenciaBloqueada(idAsistencia);
        validarAsistenciaActiva(asistencia);
        String motivoValidado = validarMotivo(motivo);
        LocalDateTime entrada = asistencia.getFechaEntradaUtc();
        LocalDateTime salida = asistencia.getFechaSalidaUtc();
        asistencia.setEstado(EstadoAsistenciaLaboral.ANULADA);
        asistenciaRepository.saveAndFlush(asistencia);
        guardarAuditoria(asistencia, actor, TipoOperacionAuditoriaAsistenciaLaboral.ANULACION,
                motivoValidado, EstadoAsistenciaLaboral.ACTIVA,
                EstadoAsistenciaLaboral.ANULADA, entrada, salida, entrada, salida);
        return asistencia;
    }

    @Transactional(readOnly = true)
    public AsistenciaLaboralHistorialRespuesta consultarHistorialAsistencia(
            Long idAsistencia, String correoAutenticado) {
        validarAdministrador(correoAutenticado);
        if (idAsistencia == null || idAsistencia <= 0) {
            throw new IllegalArgumentException("El ID de asistencia debe ser positivo.");
        }

        AsistenciaLaboralDetalleConsulta detalle = asistenciaRepository
                .consultarDetalleHistorial(idAsistencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la asistencia solicitada."));
        List<AsistenciaLaboralHistorialRespuesta.Evento> eventos = eventoRepository
                .consultarHistorialPorAsistencia(idAsistencia).stream()
                .map(this::mapearEventoHistorial)
                .toList();
        List<AsistenciaLaboralHistorialRespuesta.Auditoria> auditorias = auditoriaRepository
                .consultarHistorialPorAsistencia(idAsistencia).stream()
                .map(this::mapearAuditoriaHistorial)
                .toList();

        return new AsistenciaLaboralHistorialRespuesta(
                detalle.getIdAsistencia(),
                persona(detalle.getIdEmpleado(), detalle.getNombresEmpleado(),
                        detalle.getApellidosEmpleado(), detalle.getCorreoEmpleado()),
                aInstantUtc(detalle.getFechaEntradaUtc()),
                aInstantUtc(detalle.getFechaSalidaUtc()),
                detalle.getFechaLaboral(), detalle.getEstado(),
                persona(detalle.getIdRegistradorEntrada(),
                        detalle.getNombresRegistradorEntrada(),
                        detalle.getApellidosRegistradorEntrada(),
                        detalle.getCorreoRegistradorEntrada()),
                detalle.getIdRegistradorSalida() == null ? null : persona(
                        detalle.getIdRegistradorSalida(), detalle.getNombresRegistradorSalida(),
                        detalle.getApellidosRegistradorSalida(),
                        detalle.getCorreoRegistradorSalida()),
                eventos, auditorias);
    }

    private AsistenciaLaboralHistorialRespuesta.Evento mapearEventoHistorial(
            EventoAsistenciaHistorialConsulta evento) {
        return new AsistenciaLaboralHistorialRespuesta.Evento(
                evento.getIdEvento(), evento.getTipoEvento(),
                aInstantUtc(evento.getFechaHoraUtc()),
                persona(evento.getIdRegistrador(), evento.getNombresRegistrador(),
                        evento.getApellidosRegistrador(), evento.getCorreoRegistrador()));
    }

    private AsistenciaLaboralHistorialRespuesta.Auditoria mapearAuditoriaHistorial(
            AuditoriaAsistenciaHistorialConsulta auditoria) {
        return new AsistenciaLaboralHistorialRespuesta.Auditoria(
                auditoria.getIdAuditoria(), auditoria.getTipoOperacion(), auditoria.getMotivo(),
                aInstantUtc(auditoria.getFechaHoraUtc()),
                persona(auditoria.getIdActor(), auditoria.getNombresActor(),
                        auditoria.getApellidosActor(), auditoria.getCorreoActor()),
                auditoria.getEstadoAnterior(), auditoria.getEstadoNuevo(),
                aInstantUtc(auditoria.getFechaEntradaAnteriorUtc()),
                aInstantUtc(auditoria.getFechaSalidaAnteriorUtc()),
                aInstantUtc(auditoria.getFechaEntradaNuevaUtc()),
                aInstantUtc(auditoria.getFechaSalidaNuevaUtc()));
    }

    private AsistenciaLaboralHistorialRespuesta.Persona persona(
            Long id, String nombres, String apellidos, String correo) {
        return new AsistenciaLaboralHistorialRespuesta.Persona(id, nombres, apellidos, correo);
    }

    private Instant aInstantUtc(LocalDateTime fechaUtc) {
        return fechaUtc == null ? null : fechaUtc.toInstant(ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public ReporteAsistenciaLaboralEmpleadoRespuesta consultarReporteEmpleado(
            Long idEmpleado, LocalDate fechaDesde, LocalDate fechaHasta,
            String correoAutenticado) {
        validarAdministrador(correoAutenticado);
        if (idEmpleado == null || idEmpleado <= 0) {
            throw new IllegalArgumentException("El ID del empleado debe ser positivo.");
        }
        if (fechaDesde == null || fechaHasta == null) {
            throw new IllegalArgumentException("Las fechas desde y hasta son obligatorias.");
        }
        if (fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException(
                    "fechaDesde no puede ser posterior a fechaHasta.");
        }
        if (excedeDoceMesesCalendario(fechaDesde, fechaHasta)) {
            throw new IllegalArgumentException(
                    "El período del reporte no puede superar 12 meses calendario.");
        }
        if (!usuarioRepository.existsById(idEmpleado)) {
            throw new RecursoNoEncontradoException(
                    "No se encontró el usuario con id " + idEmpleado + ".");
        }

        long activas = 0;
        long anuladas = 0;
        long cerradas = 0;
        long abiertas = 0;
        BigDecimal microsegundosRegistrados = BigDecimal.ZERO;

        for (AsistenciaLaboralReporteConsulta asistencia
                : asistenciaRepository.consultarDatosReporteEmpleado(
                        idEmpleado, fechaDesde, fechaHasta)) {
            if (asistencia.getEstado() == EstadoAsistenciaLaboral.ANULADA) {
                anuladas++;
            } else if (asistencia.getEstado() == EstadoAsistenciaLaboral.ACTIVA) {
                activas++;
                if (asistencia.getFechaSalidaUtc() == null) {
                    abiertas++;
                } else {
                    cerradas++;
                    microsegundosRegistrados = microsegundosRegistrados.add(
                            microsegundosEntre(asistencia.getFechaEntradaUtc(),
                                    asistencia.getFechaSalidaUtc()));
                }
            }
        }

        BigDecimal horas = microsegundosRegistrados.divide(
                MICROS_POR_HORA, 2, RoundingMode.HALF_UP);
        return new ReporteAsistenciaLaboralEmpleadoRespuesta(
                idEmpleado, fechaDesde, fechaHasta, activas, anuladas,
                cerradas, abiertas, horas);
    }

    private boolean excedeDoceMesesCalendario(LocalDate fechaDesde, LocalDate fechaHasta) {
        try {
            return fechaHasta.isAfter(fechaDesde.plusMonths(12));
        } catch (DateTimeException ex) {
            // A valid date near LocalDate.MAX cannot have a later date 12 months away.
            return false;
        }
    }

    private BigDecimal microsegundosEntre(LocalDateTime entradaUtc, LocalDateTime salidaUtc) {
        LocalDateTime entrada = entradaUtc.truncatedTo(ChronoUnit.MICROS);
        LocalDateTime salida = salidaUtc.truncatedTo(ChronoUnit.MICROS);
        Duration duracion = Duration.between(entrada, salida);
        return BigDecimal.valueOf(duracion.getSeconds())
                .multiply(MICROS_POR_SEGUNDO)
                .add(BigDecimal.valueOf(duracion.getNano() / 1_000L));
    }

    @Transactional(readOnly = true)
    public Page<AsistenciaLaboralRespuesta> consultarMias(
            String correoAutenticado, int pagina, int tamano,
            LocalDate fechaDesde, LocalDate fechaHasta) {
        Usuario usuario = obtenerUsuarioAutenticado(correoAutenticado);
        String rol = nombreRol(usuario);
        if (!Set.of(ROL_RECEPCIONISTA, ROL_ENTRENADOR, ROL_NUTRICIONISTA).contains(rol)) {
            throw new AccessDeniedException("El rol no puede consultar asistencias propias.");
        }
        return consultar(usuario.getIdUsuario(), pagina, tamano, fechaDesde, fechaHasta,
                EstadoAsistenciaLaboral.ACTIVA);
    }

    @Transactional(readOnly = true)
    public Page<AsistenciaLaboralRespuesta> consultarTodas(
            String correoAutenticado, int pagina, int tamano,
            LocalDate fechaDesde, LocalDate fechaHasta, EstadoAsistenciaLaboral estado) {
        validarAdministrador(correoAutenticado);
        return consultar(null, pagina, tamano, fechaDesde, fechaHasta, estado);
    }

    @Transactional(readOnly = true)
    public Page<AsistenciaLaboralRespuesta> consultarEmpleado(
            Long idEmpleado, String correoAutenticado, int pagina, int tamano,
            LocalDate fechaDesde, LocalDate fechaHasta, EstadoAsistenciaLaboral estado) {
        validarAdministrador(correoAutenticado);
        if (idEmpleado == null || idEmpleado <= 0 || !usuarioRepository.existsById(idEmpleado)) {
            throw new RecursoNoEncontradoException(
                    "No se encontró el usuario con id " + idEmpleado + ".");
        }
        return consultar(idEmpleado, pagina, tamano, fechaDesde, fechaHasta, estado);
    }

    private Page<AsistenciaLaboralRespuesta> consultar(
            Long idEmpleado, int pagina, int tamano,
            LocalDate fechaDesde, LocalDate fechaHasta, EstadoAsistenciaLaboral estado) {
        if (pagina < 0) {
            throw new IllegalArgumentException("page debe ser mayor o igual que 0.");
        }
        if (!TAMANOS_PAGINA_PERMITIDOS.contains(tamano)) {
            throw new IllegalArgumentException("size debe ser 10, 20, 50 o 100.");
        }
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new IllegalArgumentException("fechaDesde no puede ser posterior a fechaHasta.");
        }
        PageRequest pageable = PageRequest.of(pagina, tamano,
                Sort.by(Sort.Order.desc("fechaEntradaUtc"),
                        Sort.Order.desc("idAsistencia")));
        return asistenciaRepository.consultarHistorial(
                        idEmpleado, fechaDesde, fechaHasta, estado, pageable)
                .map(AsistenciaLaboralRespuesta::desde);
    }

    private void validarAdministrador(String correoAutenticado) {
        Usuario usuario = obtenerUsuarioAutenticado(correoAutenticado);
        if (!ROL_ADMINISTRADOR.equals(nombreRol(usuario))) {
            throw new AccessDeniedException("Solo un administrador puede consultar el historial.");
        }
    }

    private Usuario obtenerUsuarioAutenticado(String correoAutenticado) {
        if (correoAutenticado == null || correoAutenticado.isBlank()) {
            throw new IllegalArgumentException("No se pudo determinar la identidad autenticada.");
        }
        return usuarioRepository.findByCorreo(correoAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el usuario autenticado."));
    }

    /** Reads only the employee ID first, then always locks employee before attendance. */
    private AsistenciaLaboral obtenerAsistenciaBloqueada(Long idAsistencia) {
        if (idAsistencia == null || idAsistencia <= 0) {
            throw new IllegalArgumentException("El ID de asistencia debe ser positivo.");
        }
        Long idEmpleadoLeido = asistenciaRepository.findIdUsuarioByIdAsistencia(idAsistencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la asistencia solicitada."));
        obtenerEmpleadoBloqueado(idEmpleadoLeido);
        AsistenciaLaboral asistencia = asistenciaRepository.findLockedByIdAsistencia(idAsistencia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la asistencia solicitada."));
        if (!idEmpleadoLeido.equals(asistencia.getUsuario().getIdUsuario())) {
            throw new ConflictoException(
                    "Cambió el empleado asociado durante la operación; vuelva a intentarlo.");
        }
        return asistencia;
    }

    private void validarAsistenciaActiva(AsistenciaLaboral asistencia) {
        if (asistencia.getEstado() != EstadoAsistenciaLaboral.ACTIVA) {
            throw new ConflictoException("La asistencia ya está anulada.");
        }
    }

    private String validarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio.");
        }
        String normalizado = motivo.trim();
        if (normalizado.codePointCount(0, normalizado.length()) > 500) {
            throw new IllegalArgumentException("El motivo no puede superar 500 caracteres.");
        }
        return normalizado;
    }

    private LocalDateTime aUtcMicrosegundos(Instant instante) {
        if (instante == null) {
            throw new IllegalArgumentException("El instante es obligatorio.");
        }
        return LocalDateTime.ofInstant(instante.truncatedTo(ChronoUnit.MICROS), ZoneOffset.UTC);
    }

    private void validarSinSolapamientos(
            AsistenciaLaboral asistencia, LocalDateTime entrada, LocalDateTime salida) {
        Long idEmpleado = asistencia.getUsuario().getIdUsuario();
        Long idAsistencia = asistencia.getIdAsistencia();
        boolean haySolapamiento = salida == null
                ? !asistenciaRepository.findSolapamientosSesionAbierta(idEmpleado, entrada,
                        EstadoAsistenciaLaboral.ACTIVA, idAsistencia).isEmpty()
                : !asistenciaRepository.findSolapamientos(idEmpleado, entrada, salida,
                        idAsistencia, EstadoAsistenciaLaboral.ACTIVA).isEmpty();
        if (haySolapamiento) {
            throw new ConflictoException(
                    "El intervalo corregido se solapa con otra asistencia activa.");
        }
    }

    private void guardarAuditoria(
            AsistenciaLaboral asistencia,
            Usuario actor,
            TipoOperacionAuditoriaAsistenciaLaboral operacion,
            String motivo,
            EstadoAsistenciaLaboral estadoAnterior,
            EstadoAsistenciaLaboral estadoNuevo,
            LocalDateTime entradaAnterior,
            LocalDateTime salidaAnterior,
            LocalDateTime entradaNueva,
            LocalDateTime salidaNueva) {
        AuditoriaAsistenciaLaboral auditoria = AuditoriaAsistenciaLaboral.crear(
                asistencia, actor, ahoraUtcConPrecisionMicrosegundos(), operacion, motivo,
                estadoAnterior, estadoNuevo, entradaAnterior, salidaAnterior,
                entradaNueva, salidaNueva);
        auditoriaRepository.save(auditoria);
    }

    private String nombreRol(Usuario usuario) {
        return usuario.getRol() == null || usuario.getRol().getNombre() == null
                ? "" : usuario.getRol().getNombre().trim().toUpperCase(Locale.ROOT);
    }

    private Usuario obtenerRegistradorRecepcionista(Long idRegistrador) {
        Usuario registrador = usuarioRepository.findByIdUsuario(idRegistrador)
                .orElseThrow(() -> noEncontrado(idRegistrador));
        if (!tieneRol(registrador, ROL_RECEPCIONISTA)) {
            throw new AccessDeniedException(
                    "Solo una recepcionista puede registrar asistencias laborales.");
        }
        return registrador;
    }

    private Long obtenerIdRegistradorAutenticado(String correoAutenticado) {
        if (correoAutenticado == null || correoAutenticado.isBlank()) {
            throw new IllegalArgumentException(
                    "No se pudo determinar la identidad autenticada.");
        }
        return usuarioRepository.findByCorreo(correoAutenticado)
                .map(Usuario::getIdUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el usuario autenticado."));
    }

    private Usuario obtenerEmpleadoBloqueado(Long idEmpleado) {
        return usuarioRepository.findLockedById(idEmpleado)
                .orElseThrow(() -> noEncontrado(idEmpleado));
    }

    private void validarEmpleadoPermitido(Usuario empleado, Usuario registrador) {
        if (tieneRol(empleado, ROL_ENTRENADOR)
                || tieneRol(empleado, ROL_NUTRICIONISTA)) {
            return;
        }
        if (tieneRol(empleado, ROL_RECEPCIONISTA)
                && empleado.getIdUsuario().equals(registrador.getIdUsuario())) {
            return;
        }
        throw new AccessDeniedException(
                "La recepcionista solo puede registrar su asistencia o la de entrenadores y nutricionistas.");
    }

    private void guardarEvento(AsistenciaLaboral asistencia,
            TipoEventoAsistenciaLaboral tipo, Instant instante, Usuario registrador) {
        EventoAsistenciaLaboral evento = new EventoAsistenciaLaboral();
        evento.setAsistencia(asistencia);
        evento.setTipoEvento(tipo);
        evento.setFechaHoraUtc(instante);
        evento.setUsuarioRegistrador(registrador);
        eventoRepository.saveAndFlush(evento);
    }

    private Instant ahoraUtcConPrecisionMicrosegundos() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private void validarIds(Long idEmpleado, Long idRegistrador) {
        if (idEmpleado == null || idRegistrador == null) {
            throw new IllegalArgumentException(
                    "El empleado y el registrador son obligatorios.");
        }
    }

    private boolean tieneRol(Usuario usuario, String rolEsperado) {
        return usuario.getRol() != null && usuario.getRol().getNombre() != null
                && usuario.getRol().getNombre().trim().toUpperCase(Locale.ROOT)
                        .equals(rolEsperado);
    }

    private RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException(
                "No se encontró el usuario con id " + id + ".");
    }
}
