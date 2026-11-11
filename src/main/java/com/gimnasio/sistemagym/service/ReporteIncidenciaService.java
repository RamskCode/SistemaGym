package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.CrearReporteIncidenciaRequest;
import com.gimnasio.sistemagym.dto.CambiarEstadoIncidenciaRequest;
import com.gimnasio.sistemagym.dto.HistorialIncidenciaRespuesta;
import com.gimnasio.sistemagym.dto.ReporteIncidenciaRespuesta;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.EstadoIncidencia;
import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import com.gimnasio.sistemagym.repository.HistorialReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.ReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReporteIncidenciaService {

    private final ReporteIncidenciaRepository reporteRepository;
    private final HistorialReporteIncidenciaRepository historialRepository;
    private final ElementoRevisionRepository elementoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public ReporteIncidenciaService(
            ReporteIncidenciaRepository reporteRepository,
            HistorialReporteIncidenciaRepository historialRepository,
            ElementoRevisionRepository elementoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock) {
        this.reporteRepository = reporteRepository;
        this.historialRepository = historialRepository;
        this.elementoRepository = elementoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional
    public ReporteIncidenciaRespuesta crearDirecto(
            CrearReporteIncidenciaRequest request, String correoAutenticado) {
        Usuario entrenador = usuario(correoAutenticado);
        ElementoRevision elemento = elementoActivo(request.idElemento());
        LocalDateTime ahora = ahoraUtc();

        ReporteIncidencia reporte = new ReporteIncidencia();
        reporte.setEntrenador(entrenador);
        reporte.setElemento(elemento);
        reporte.setDescripcion(request.descripcion().trim());
        reporte.setPrioridad(request.prioridad());
        reporte.setEstado(EstadoIncidencia.PENDIENTE);
        reporte.setFechaReporte(ahora);
        reporte.setFechaActualizacion(ahora);
        reporte = reporteRepository.save(reporte);

        HistorialReporteIncidencia evento = eventoInicial(reporte, entrenador, ahora);
        historialRepository.save(evento);
        return respuesta(reporte, List.of(evento));
    }

    @Transactional
    public ReporteIncidenciaRespuesta cambiarEstado(
            Long idReporte, CambiarEstadoIncidenciaRequest request,
            String correoAdministrador) {
        ReporteIncidencia reporte = reporteRepository.findLockedById(idReporte)
                .orElseThrow(() -> noEncontrado(idReporte));
        EstadoIncidencia anterior = reporte.getEstado();
        EstadoIncidencia nuevo = request.estado();

        if (anterior == nuevo) {
            throw new IllegalStateException("El reporte ya se encuentra en el estado solicitado.");
        }
        if (!transicionPermitida(anterior, nuevo)) {
            throw new IllegalStateException("La transición de estado solicitada no está permitida.");
        }

        Usuario administrador = usuario(correoAdministrador);
        LocalDateTime ahora = ahoraUtc();
        String observacion = request.observacion().trim();
        reporte.setEstado(nuevo);
        reporte.setFechaActualizacion(ahora);
        reporte.setFechaSolucion(nuevo == EstadoIncidencia.SOLUCIONADO ? ahora : null);
        reporte.setObservacionesActuales(observacion);
        reporteRepository.save(reporte);

        HistorialReporteIncidencia evento = new HistorialReporteIncidencia();
        evento.setReporte(reporte);
        evento.setUsuario(administrador);
        evento.setEstadoAnterior(anterior);
        evento.setEstadoNuevo(nuevo);
        evento.setObservacion(observacion);
        evento.setFechaHora(ahora);
        historialRepository.save(evento);

        List<HistorialReporteIncidencia> historial =
                historialRepository.findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(
                        idReporte);
        return respuesta(reporte, historial);
    }

    private boolean transicionPermitida(EstadoIncidencia anterior, EstadoIncidencia nuevo) {
        return (anterior == EstadoIncidencia.PENDIENTE
                    && nuevo == EstadoIncidencia.EN_REVISION)
                || (anterior == EstadoIncidencia.EN_REVISION
                    && nuevo == EstadoIncidencia.SOLUCIONADO)
                || (anterior == EstadoIncidencia.SOLUCIONADO
                    && nuevo == EstadoIncidencia.EN_REVISION);
    }

    public List<ReporteIncidenciaRespuesta> listarMios(String correoAutenticado) {
        Usuario entrenador = usuario(correoAutenticado);
        return respuestas(reporteRepository
                .findByEntrenador_IdUsuarioOrderByFechaReporteDescIdReporteDesc(
                        entrenador.getIdUsuario()));
    }

    public List<ReporteIncidenciaRespuesta> listarTodos() {
        return respuestas(reporteRepository.findAllByOrderByFechaReporteDescIdReporteDesc());
    }

    public ReporteIncidenciaRespuesta buscar(
            Long id, String correoAutenticado, boolean administrador) {
        ReporteIncidencia reporte;
        if (administrador) {
            reporte = reporteRepository.findById(id).orElseThrow(() -> noEncontrado(id));
        } else {
            Usuario entrenador = usuario(correoAutenticado);
            reporte = reporteRepository.findByIdReporteAndEntrenador_IdUsuario(
                            id, entrenador.getIdUsuario())
                    .orElseThrow(() -> noEncontrado(id));
        }
        return respuesta(reporte,
                historialRepository.findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(
                        reporte.getIdReporte()));
    }

    private List<ReporteIncidenciaRespuesta> respuestas(List<ReporteIncidencia> reportes) {
        if (reportes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = reportes.stream().map(ReporteIncidencia::getIdReporte).toList();
        Map<Long, List<HistorialReporteIncidencia>> eventos = historialRepository
                .findByReporte_IdReporteInOrderByFechaHoraAscIdHistorialAsc(ids).stream()
                .collect(Collectors.groupingBy(e -> e.getReporte().getIdReporte()));
        return reportes.stream().map(reporte -> respuesta(
                reporte, eventos.getOrDefault(reporte.getIdReporte(), List.of()))).toList();
    }

    private ReporteIncidenciaRespuesta respuesta(
            ReporteIncidencia reporte, List<HistorialReporteIncidencia> eventos) {
        return ReporteIncidenciaRespuesta.desde(reporte, eventos.stream()
                .map(HistorialIncidenciaRespuesta::desde).toList());
    }

    HistorialReporteIncidencia eventoInicial(
            ReporteIncidencia reporte, Usuario actor, LocalDateTime ahora) {
        HistorialReporteIncidencia evento = new HistorialReporteIncidencia();
        evento.setReporte(reporte);
        evento.setUsuario(actor);
        evento.setEstadoAnterior(null);
        evento.setEstadoNuevo(EstadoIncidencia.PENDIENTE);
        evento.setFechaHora(ahora);
        return evento;
    }

    Usuario usuario(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el usuario autenticado."));
    }

    ElementoRevision elementoActivo(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El ID del elemento debe ser mayor que cero.");
        }
        ElementoRevision elemento = elementoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un elemento con el ID " + id + "."));
        if (!elemento.isActivo()) {
            throw new IllegalArgumentException("No se puede registrar una incidencia en un elemento inactivo.");
        }
        return elemento;
    }

    private LocalDateTime ahoraUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException(
                "No existe un reporte de incidencia con el ID " + id + ".");
    }
}
