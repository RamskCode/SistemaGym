package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ChecklistSalaRespuesta;
import com.gimnasio.sistemagym.dto.CrearChecklistRequest;
import com.gimnasio.sistemagym.dto.DetalleChecklistRequest;
import com.gimnasio.sistemagym.dto.DetalleChecklistRespuesta;
import com.gimnasio.sistemagym.entity.ChecklistSala;
import com.gimnasio.sistemagym.entity.DetalleChecklist;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.EstadoIncidencia;
import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import com.gimnasio.sistemagym.entity.PrioridadIncidencia;
import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import com.gimnasio.sistemagym.entity.ResultadoChecklist;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ChecklistSalaRepository;
import com.gimnasio.sistemagym.repository.DetalleChecklistRepository;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import com.gimnasio.sistemagym.repository.HistorialReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.ReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ChecklistSalaService {

    private final ChecklistSalaRepository checklistRepository;
    private final DetalleChecklistRepository detalleRepository;
    private final ReporteIncidenciaRepository reporteRepository;
    private final HistorialReporteIncidenciaRepository historialRepository;
    private final ElementoRevisionRepository elementoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Clock clock;

    public ChecklistSalaService(
            ChecklistSalaRepository checklistRepository,
            DetalleChecklistRepository detalleRepository,
            ReporteIncidenciaRepository reporteRepository,
            HistorialReporteIncidenciaRepository historialRepository,
            ElementoRevisionRepository elementoRepository,
            UsuarioRepository usuarioRepository,
            Clock clock) {
        this.checklistRepository = checklistRepository;
        this.detalleRepository = detalleRepository;
        this.reporteRepository = reporteRepository;
        this.historialRepository = historialRepository;
        this.elementoRepository = elementoRepository;
        this.usuarioRepository = usuarioRepository;
        this.clock = clock;
    }

    @Transactional
    public ChecklistSalaRespuesta crear(CrearChecklistRequest request, String correoAutenticado) {
        validarRequest(request);
        Usuario entrenador = usuario(correoAutenticado);
        Set<Long> idsElemento = request.detalles().stream()
                .map(DetalleChecklistRequest::idElemento).collect(Collectors.toSet());
        List<ElementoRevision> elementos = elementoRepository
                .findAllByIdElementoInAndActivoTrue(idsElemento);
        if (elementos.size() != idsElemento.size()) {
            throw new IllegalArgumentException(
                    "Uno o más elementos no existen o están inactivos.");
        }
        Map<Long, ElementoRevision> porId = elementos.stream().collect(
                Collectors.toMap(ElementoRevision::getIdElemento, Function.identity()));

        LocalDateTime ahora = ahoraUtc();
        ChecklistSala checklist = new ChecklistSala();
        checklist.setEntrenador(entrenador);
        checklist.setFechaHora(ahora);
        checklist.setObservaciones(normalizarOpcional(request.observaciones()));
        checklist = checklistRepository.save(checklist);

        List<DetalleChecklist> detalles = new ArrayList<>();
        Map<Long, ReporteIncidencia> reportesPorDetalle = new HashMap<>();
        for (DetalleChecklistRequest item : request.detalles()) {
            ElementoRevision elemento = porId.get(item.idElemento());
            DetalleChecklist detalle = new DetalleChecklist();
            detalle.setChecklist(checklist);
            detalle.setElemento(elemento);
            detalle.setNombreElementoSnapshot(elemento.getNombre());
            detalle.setTipoElementoSnapshot(elemento.getTipo());
            detalle.setUbicacionSnapshot(elemento.getUbicacion());
            detalle.setResultado(item.resultado());
            detalle.setObservaciones(normalizarOpcional(item.observaciones()));
            detalle.setFechaRevision(ahora);
            detalle = detalleRepository.save(detalle);
            detalles.add(detalle);

            if (item.resultado() == ResultadoChecklist.CON_PROBLEMA) {
                ReporteIncidencia reporte = crearReporte(
                        entrenador, elemento, detalle, item.descripcionIncidencia(),
                        item.prioridadIncidencia(), ahora);
                reportesPorDetalle.put(detalle.getIdDetalle(), reporte);
            }
        }
        return respuesta(checklist, detalles, reportesPorDetalle);
    }

    public List<ChecklistSalaRespuesta> listarMios(String correoAutenticado) {
        Usuario entrenador = usuario(correoAutenticado);
        return respuestas(checklistRepository
                .findByEntrenador_IdUsuarioOrderByFechaHoraDescIdChecklistDesc(
                        entrenador.getIdUsuario()));
    }

    public List<ChecklistSalaRespuesta> listarTodos() {
        return respuestas(checklistRepository.findAllByOrderByFechaHoraDescIdChecklistDesc());
    }

    public ChecklistSalaRespuesta buscar(Long id, String correoAutenticado,
            boolean administrador) {
        ChecklistSala checklist;
        if (administrador) {
            checklist = checklistRepository.findById(id)
                    .orElseThrow(() -> noEncontrado(id));
        } else {
            Usuario entrenador = usuario(correoAutenticado);
            checklist = checklistRepository.findByIdChecklistAndEntrenador_IdUsuario(
                            id, entrenador.getIdUsuario())
                    .orElseThrow(() -> noEncontrado(id));
        }
        return respuestas(List.of(checklist)).getFirst();
    }

    private List<ChecklistSalaRespuesta> respuestas(List<ChecklistSala> checklists) {
        if (checklists.isEmpty()) {
            return List.of();
        }
        List<Long> checklistIds = checklists.stream()
                .map(ChecklistSala::getIdChecklist).toList();
        List<DetalleChecklist> detalles = detalleRepository
                .findByChecklist_IdChecklistInOrderByIdDetalleAsc(checklistIds);
        Map<Long, List<DetalleChecklist>> detallesPorChecklist = detalles.stream()
                .collect(Collectors.groupingBy(d -> d.getChecklist().getIdChecklist()));
        Map<Long, ReporteIncidencia> reportesPorDetalle = cargarReportes(detalles);
        return checklists.stream().map(checklist -> respuesta(checklist,
                detallesPorChecklist.getOrDefault(checklist.getIdChecklist(), List.of()),
                reportesPorDetalle)).toList();
    }

    private Map<Long, ReporteIncidencia> cargarReportes(List<DetalleChecklist> detalles) {
        if (detalles.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = detalles.stream().map(DetalleChecklist::getIdDetalle).toList();
        return reporteRepository.findByDetalleChecklist_IdDetalleIn(ids).stream()
                .collect(Collectors.toMap(r -> r.getDetalleChecklist().getIdDetalle(),
                        Function.identity()));
    }

    private ChecklistSalaRespuesta respuesta(ChecklistSala checklist,
            List<DetalleChecklist> detalles, Map<Long, ReporteIncidencia> reportes) {
        List<DetalleChecklistRespuesta> dtoDetalles = detalles.stream()
                .map(detalle -> {
                    ReporteIncidencia reporte = reportes.get(detalle.getIdDetalle());
                    return DetalleChecklistRespuesta.desde(detalle, reporte);
                }).toList();
        return ChecklistSalaRespuesta.desde(checklist, dtoDetalles);
    }

    private ReporteIncidencia crearReporte(Usuario entrenador, ElementoRevision elemento,
            DetalleChecklist detalle, String descripcion, PrioridadIncidencia prioridad,
            LocalDateTime ahora) {
        ReporteIncidencia reporte = new ReporteIncidencia();
        reporte.setEntrenador(entrenador);
        reporte.setElemento(elemento);
        reporte.setDetalleChecklist(detalle);
        reporte.setDescripcion(descripcion.trim());
        reporte.setPrioridad(prioridad);
        reporte.setEstado(EstadoIncidencia.PENDIENTE);
        reporte.setFechaReporte(ahora);
        reporte.setFechaActualizacion(ahora);
        reporte = reporteRepository.save(reporte);

        HistorialReporteIncidencia evento = new HistorialReporteIncidencia();
        evento.setReporte(reporte);
        evento.setUsuario(entrenador);
        evento.setEstadoAnterior(null);
        evento.setEstadoNuevo(EstadoIncidencia.PENDIENTE);
        evento.setFechaHora(ahora);
        historialRepository.save(evento);
        return reporte;
    }

    private void validarRequest(CrearChecklistRequest request) {
        if (request == null || request.detalles() == null || request.detalles().isEmpty()) {
            throw new IllegalArgumentException(
                    "El checklist debe incluir al menos un elemento.");
        }
        Set<Long> ids = new HashSet<>();
        for (DetalleChecklistRequest detalle : request.detalles()) {
            if (detalle == null || detalle.idElemento() == null
                    || detalle.idElemento() <= 0 || detalle.resultado() == null) {
                throw new IllegalArgumentException(
                        "Cada detalle debe incluir un elemento válido y un resultado.");
            }
            if (!ids.add(detalle.idElemento())) {
                throw new IllegalArgumentException(
                        "No se puede repetir un elemento dentro del checklist.");
            }
            boolean tieneDescripcion = detalle.descripcionIncidencia() != null;
            boolean tienePrioridad = detalle.prioridadIncidencia() != null;
            if (detalle.resultado() == ResultadoChecklist.CON_PROBLEMA) {
                if (detalle.descripcionIncidencia() == null
                        || detalle.descripcionIncidencia().isBlank()) {
                    throw new IllegalArgumentException(
                            "Cada elemento con problema requiere descripción de incidencia.");
                }
                if (detalle.prioridadIncidencia() == null) {
                    throw new IllegalArgumentException(
                            "Cada elemento con problema requiere prioridad de incidencia.");
                }
            } else if (tieneDescripcion || tienePrioridad) {
                throw new IllegalArgumentException(
                        "Un elemento correcto no puede incluir datos de incidencia.");
            }
        }
    }

    private String normalizarOpcional(String valor) {
        if (valor == null) return null;
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private Usuario usuario(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el usuario autenticado."));
    }

    private LocalDateTime ahoraUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException(
                "No existe un checklist con el ID " + id + ".");
    }
}
