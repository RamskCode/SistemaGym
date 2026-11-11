package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.CrearChecklistRequest;
import com.gimnasio.sistemagym.dto.CrearReporteIncidenciaRequest;
import com.gimnasio.sistemagym.dto.DetalleChecklistRequest;
import com.gimnasio.sistemagym.entity.ChecklistSala;
import com.gimnasio.sistemagym.entity.DetalleChecklist;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.EstadoIncidencia;
import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import com.gimnasio.sistemagym.entity.PrioridadIncidencia;
import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import com.gimnasio.sistemagym.entity.ResultadoChecklist;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ChecklistSalaRepository;
import com.gimnasio.sistemagym.repository.DetalleChecklistRepository;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import com.gimnasio.sistemagym.repository.HistorialReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.ReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChecklistSalaServiceTest {
    @Mock ChecklistSalaRepository checklistRepository;
    @Mock DetalleChecklistRepository detalleRepository;
    @Mock ReporteIncidenciaRepository reporteRepository;
    @Mock HistorialReporteIncidenciaRepository historialRepository;
    @Mock ElementoRevisionRepository elementoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock Clock clock;
    @InjectMocks ChecklistSalaService checklistService;
    @InjectMocks ReporteIncidenciaService reporteService;

    private Usuario entrenador;
    private ElementoRevision elementoUno;
    private ElementoRevision elementoDos;

    @BeforeEach
    void preparar() {
        entrenador = org.mockito.Mockito.mock(Usuario.class);
        when(entrenador.getCorreo()).thenReturn("coach@example.test");
        org.mockito.Mockito.lenient().when(entrenador.getIdUsuario()).thenReturn(41L);
        elementoUno = elemento(1L, "Cinta", true);
        elementoDos = elemento(2L, "Bicicleta", true);
        org.mockito.Mockito.lenient().when(clock.instant())
                .thenReturn(Instant.parse("2026-11-04T16:00:00Z"));
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void checklistCorrectoGuardaSnapshotSinCrearIncidencias() {
        prepararCreacion(List.of(elementoUno));

        var resultado = checklistService.crear(new CrearChecklistRequest(List.of(
                new DetalleChecklistRequest(1L, ResultadoChecklist.CORRECTO,
                        "  Sin ruidos  ", null, null)), "  Revisión  "),
                entrenador.getCorreo());

        assertEquals(41L, resultado.idEntrenador());
        assertEquals("Cinta", resultado.detalles().getFirst().nombreElemento());
        assertEquals("Sin ruidos", resultado.detalles().getFirst().observaciones());
        assertNull(resultado.detalles().getFirst().incidencia());
        verify(reporteRepository, never()).save(any());
        verify(historialRepository, never()).save(any());
    }

    @Test
    void cadaDetalleConProblemaCreaSuReporteEHistorialInicial() {
        prepararCreacion(List.of(elementoUno, elementoDos));

        var resultado = checklistService.crear(new CrearChecklistRequest(List.of(
                new DetalleChecklistRequest(1L, ResultadoChecklist.CON_PROBLEMA,
                        null, "Correa gastada", PrioridadIncidencia.ALTA),
                new DetalleChecklistRequest(2L, ResultadoChecklist.CON_PROBLEMA,
                        null, "Pedal flojo", PrioridadIncidencia.MEDIA)), null),
                entrenador.getCorreo());

        assertEquals(2, resultado.detalles().size());
        assertEquals(2, resultado.detalles().stream()
                .filter(d -> d.incidencia() != null).count());
        verify(reporteRepository, org.mockito.Mockito.times(2)).save(any());
        verify(historialRepository, org.mockito.Mockito.times(2)).save(any());
        assertEquals("PENDIENTE", resultado.detalles().getFirst().incidencia().estado());
    }

    @Test
    void checklistVacioSeRechazaAntesDePersistir() {
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(List.of(), null),
                        entrenador.getCorreo()));
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void elementoDuplicadoSeRechaza() {
        var detalle = new DetalleChecklistRequest(1L, ResultadoChecklist.CORRECTO,
                null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(
                        List.of(detalle, detalle), null), entrenador.getCorreo()));
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void elementoInactivoOInexistenteSeRechaza() {
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(elementoRepository.findAllByIdElementoInAndActivoTrue(anyCollection())
        ).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(List.of(
                        new DetalleChecklistRequest(1L, ResultadoChecklist.CORRECTO,
                                null, null, null)), null), entrenador.getCorreo()));
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void problemaSinDescripcionOPrioridadSeRechaza() {
        var missingDescription = new DetalleChecklistRequest(1L,
                ResultadoChecklist.CON_PROBLEMA, null, "  ", PrioridadIncidencia.BAJA);
        var missingPriority = new DetalleChecklistRequest(1L,
                ResultadoChecklist.CON_PROBLEMA, null, "Falla", null);
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(
                        List.of(missingDescription), null), entrenador.getCorreo()));
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(
                        List.of(missingPriority), null), entrenador.getCorreo()));
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void correctoConDatosDeIncidenciaSeRechaza() {
        var item = new DetalleChecklistRequest(1L, ResultadoChecklist.CORRECTO,
                null, "descripción no permitida", null);
        assertThrows(IllegalArgumentException.class,
                () -> checklistService.crear(new CrearChecklistRequest(List.of(item), null),
                        entrenador.getCorreo()));
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void reporteDirectoIniciaPendienteYRegistraEventoInicial() {
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(elementoRepository.findById(1L)).thenReturn(Optional.of(elementoUno));
        when(reporteRepository.save(any(ReporteIncidencia.class))).thenAnswer(call -> {
            ReporteIncidencia reporte = call.getArgument(0);
            reporte.setIdReporte(93L);
            return reporte;
        });
        when(historialRepository.save(any(HistorialReporteIncidencia.class)))
                .thenAnswer(call -> call.getArgument(0));

        var respuesta = reporteService.crearDirecto(new CrearReporteIncidenciaRequest(
                1L, "  Ruido anormal  ", PrioridadIncidencia.MEDIA),
                entrenador.getCorreo());

        assertEquals("PENDIENTE", respuesta.estado());
        assertEquals("Ruido anormal", respuesta.descripcion());
        assertEquals(1, respuesta.historial().size());
        assertNull(respuesta.historial().getFirst().estadoAnterior());
        assertEquals("PENDIENTE", respuesta.historial().getFirst().estadoNuevo());
    }

    @Test
    void reporteDirectoRechazaElementoInactivo() {
        ElementoRevision inactivo = elemento(1L, "Cinta", false);
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(elementoRepository.findById(1L)).thenReturn(Optional.of(inactivo));
        assertThrows(IllegalArgumentException.class,
                () -> reporteService.crearDirecto(new CrearReporteIncidenciaRequest(
                        1L, "Falla", PrioridadIncidencia.ALTA), entrenador.getCorreo()));
        verify(reporteRepository, never()).save(any());
    }

    @Test
    void entrenadorNoPuedeConsultarChecklistAjeno() {
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(checklistRepository.findByIdChecklistAndEntrenador_IdUsuario(9L, 41L))
                .thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> checklistService.buscar(9L, entrenador.getCorreo(), false));
    }

    @Test
    void entrenadorNoPuedeConsultarReporteAjeno() {
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(reporteRepository.findByIdReporteAndEntrenador_IdUsuario(9L, 41L))
                .thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> reporteService.buscar(9L, entrenador.getCorreo(), false));
    }

    private void prepararCreacion(List<ElementoRevision> elementos) {
        when(usuarioRepository.findByCorreo(entrenador.getCorreo()))
                .thenReturn(Optional.of(entrenador));
        when(elementoRepository.findAllByIdElementoInAndActivoTrue(anyCollection()))
                .thenReturn(elementos);
        when(checklistRepository.save(any(ChecklistSala.class))).thenAnswer(call -> {
            ChecklistSala checklist = call.getArgument(0);
            checklist.setIdChecklist(77L);
            return checklist;
        });
        org.mockito.Mockito.lenient().when(detalleRepository.save(any(DetalleChecklist.class)))
                .thenAnswer(call -> {
                    DetalleChecklist detalle = call.getArgument(0);
                    detalle.setIdDetalle((long) detalle.getElemento().getIdElemento() + 100L);
                    return detalle;
                });
        org.mockito.Mockito.lenient().when(reporteRepository.save(any(ReporteIncidencia.class)))
                .thenAnswer(call -> {
                    ReporteIncidencia reporte = call.getArgument(0);
                    reporte.setIdReporte((long) reporte.getDetalleChecklist()
                            .getElemento().getIdElemento() + 200L);
                    return reporte;
                });
        org.mockito.Mockito.lenient().when(historialRepository.save(
                any(HistorialReporteIncidencia.class))).thenAnswer(call -> call.getArgument(0));
    }

    private ElementoRevision elemento(Long id, String nombre, boolean activo) {
        ElementoRevision e = new ElementoRevision();
        e.setIdElemento(id);
        e.setNombre(nombre);
        e.setTipo(TipoElementoRevision.MAQUINA);
        e.setUbicacion("Piso 1");
        e.setActivo(activo);
        return e;
    }
}
