package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.CambiarEstadoIncidenciaRequest;
import com.gimnasio.sistemagym.entity.EstadoIncidencia;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import com.gimnasio.sistemagym.repository.HistorialReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.ReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteIncidenciaTransicionTest {
    @Mock ReporteIncidenciaRepository reporteRepository;
    @Mock HistorialReporteIncidenciaRepository historialRepository;
    @Mock ElementoRevisionRepository elementoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock Clock clock;

    private ReporteIncidenciaService service;
    private Usuario admin;

    @BeforeEach
    void preparar() {
        service = new ReporteIncidenciaService(reporteRepository, historialRepository,
                elementoRepository, usuarioRepository, clock);
        admin = new Usuario();
        admin.setCorreo("admin@example.test");
        org.mockito.Mockito.lenient().when(clock.instant())
                .thenReturn(Instant.parse("2026-11-04T16:00:00Z"));
    }

    @Test
    void transicionPendienteARevisionConservaReporteYAgregaEvento() {
        ReporteIncidencia reporte = reporte(EstadoIncidencia.PENDIENTE);
        LocalDateTime fechaReporteOriginal = reporte.getFechaReporte();
        when(reporteRepository.findLockedById(9L)).thenReturn(Optional.of(reporte));
        when(usuarioRepository.findByCorreo(admin.getCorreo())).thenReturn(Optional.of(admin));
        when(historialRepository.save(any(HistorialReporteIncidencia.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(historialRepository.findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(9L))
                .thenAnswer(invocacion -> List.of(evento(EstadoIncidencia.PENDIENTE,
                        EstadoIncidencia.EN_REVISION, "Inspección programada")));

        var resultado = service.cambiarEstado(9L, new CambiarEstadoIncidenciaRequest(
                EstadoIncidencia.EN_REVISION, "  Inspección programada  "), admin.getCorreo());

        assertEquals(EstadoIncidencia.EN_REVISION, reporte.getEstado());
        assertEquals(fechaReporteOriginal, reporte.getFechaReporte());
        assertEquals(LocalDateTime.of(2026, 11, 4, 16, 0), reporte.getFechaActualizacion());
        assertNull(reporte.getFechaSolucion());
        assertEquals("Inspección programada", reporte.getObservacionesActuales());
        assertEquals(EstadoIncidencia.EN_REVISION.name(), resultado.estado());
        assertEquals(1, resultado.historial().size());
        assertEquals("Inspección programada", resultado.historial().getFirst().observacion());
        verify(reporteRepository).save(reporte);
        verify(historialRepository).save(any(HistorialReporteIncidencia.class));
    }

    @Test
    void transicionAResueltoEstableceFechaSolucion() {
        ReporteIncidencia reporte = reporte(EstadoIncidencia.EN_REVISION);
        when(reporteRepository.findLockedById(9L)).thenReturn(Optional.of(reporte));
        when(usuarioRepository.findByCorreo(admin.getCorreo())).thenReturn(Optional.of(admin));
        when(historialRepository.findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(9L))
                .thenReturn(List.of());

        service.cambiarEstado(9L, new CambiarEstadoIncidenciaRequest(
                EstadoIncidencia.SOLUCIONADO, "Mantenimiento verificado"), admin.getCorreo());

        assertEquals(LocalDateTime.of(2026, 11, 4, 16, 0), reporte.getFechaSolucion());
        assertEquals(EstadoIncidencia.SOLUCIONADO, reporte.getEstado());
    }

    @Test
    void reaperturaLimpiaFechaSolucion() {
        ReporteIncidencia reporte = reporte(EstadoIncidencia.SOLUCIONADO);
        reporte.setFechaSolucion(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(reporteRepository.findLockedById(9L)).thenReturn(Optional.of(reporte));
        when(usuarioRepository.findByCorreo(admin.getCorreo())).thenReturn(Optional.of(admin));
        when(historialRepository.findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(9L))
                .thenReturn(List.of());

        service.cambiarEstado(9L, new CambiarEstadoIncidenciaRequest(
                EstadoIncidencia.EN_REVISION, "El problema persiste"), admin.getCorreo());

        assertEquals(EstadoIncidencia.EN_REVISION, reporte.getEstado());
        assertNull(reporte.getFechaSolucion());
    }

    @Test
    void estadoRepetidoYTransicionesNoPermitidasNoEscribenNada() {
        ReporteIncidencia reporte = reporte(EstadoIncidencia.PENDIENTE);
        when(reporteRepository.findLockedById(9L)).thenReturn(Optional.of(reporte));

        assertThrows(IllegalStateException.class, () -> service.cambiarEstado(9L,
                new CambiarEstadoIncidenciaRequest(EstadoIncidencia.PENDIENTE, "Observación"),
                admin.getCorreo()));
        assertThrows(IllegalStateException.class, () -> service.cambiarEstado(9L,
                new CambiarEstadoIncidenciaRequest(EstadoIncidencia.SOLUCIONADO, "Observación"),
                admin.getCorreo()));

        verify(usuarioRepository, never()).findByCorreo(any());
        verify(reporteRepository, never()).save(any());
        verify(historialRepository, never()).save(any());
    }

    @Test
    void reporteInexistenteDevuelveExcepcionNotFoundSinCambios() {
        when(reporteRepository.findLockedById(404L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class, () -> service.cambiarEstado(404L,
                new CambiarEstadoIncidenciaRequest(EstadoIncidencia.EN_REVISION, "Revisar"),
                admin.getCorreo()));
        verify(reporteRepository, never()).save(any());
        verify(historialRepository, never()).save(any());
    }

    private ReporteIncidencia reporte(EstadoIncidencia estado) {
        ReporteIncidencia reporte = new ReporteIncidencia();
        reporte.setIdReporte(9L);
        reporte.setPrioridad(com.gimnasio.sistemagym.entity.PrioridadIncidencia.MEDIA);
        Usuario entrenador = new Usuario();
        entrenador.setCorreo("coach@example.test");
        reporte.setEntrenador(entrenador);
        ElementoRevision elemento = new ElementoRevision();
        elemento.setIdElemento(2L);
        elemento.setNombre("Cinta");
        elemento.setTipo(TipoElementoRevision.MAQUINA);
        reporte.setElemento(elemento);
        reporte.setEstado(estado);
        reporte.setFechaReporte(LocalDateTime.of(2026, 10, 20, 12, 30));
        reporte.setFechaActualizacion(reporte.getFechaReporte());
        return reporte;
    }

    private HistorialReporteIncidencia evento(EstadoIncidencia anterior,
            EstadoIncidencia nuevo, String observacion) {
        HistorialReporteIncidencia evento = new HistorialReporteIncidencia();
        evento.setEstadoAnterior(anterior);
        evento.setEstadoNuevo(nuevo);
        evento.setObservacion(observacion);
        evento.setUsuario(admin);
        evento.setIdHistorial(12L);
        evento.setFechaHora(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return evento;
    }
}
