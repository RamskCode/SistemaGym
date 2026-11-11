package com.gimnasio.sistemagym.integration.mysql;

import com.gimnasio.sistemagym.dto.CrearChecklistRequest;
import com.gimnasio.sistemagym.dto.DetalleChecklistRequest;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.PrioridadIncidencia;
import com.gimnasio.sistemagym.entity.ResultadoChecklist;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.ChecklistSalaRepository;
import com.gimnasio.sistemagym.repository.DetalleChecklistRepository;
import com.gimnasio.sistemagym.repository.ElementoRevisionRepository;
import com.gimnasio.sistemagym.repository.HistorialReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.ReporteIncidenciaRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.ChecklistSalaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class ChecklistSalaMySql84IT extends MySql84IntegrationTestSupport {

    @Autowired ChecklistSalaService checklistService;
    @Autowired ChecklistSalaRepository checklistRepository;
    @Autowired DetalleChecklistRepository detalleRepository;
    @Autowired ReporteIncidenciaRepository reporteRepository;
    @Autowired HistorialReporteIncidenciaRepository historialRepository;
    @Autowired ElementoRevisionRepository elementoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;
    @MockitoSpyBean
    ReporteIncidenciaRepository reporteRepositorySpy;

    private Usuario entrenador;
    private ElementoRevision elemento;

    @BeforeEach
    void prepararDatosAislados() {
        historialRepository.deleteAllInBatch();
        reporteRepository.deleteAllInBatch();
        detalleRepository.deleteAllInBatch();
        checklistRepository.deleteAllInBatch();
        elementoRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();

        Rol rol = new Rol();
        rol.setNombre("ENTRENADOR");
        rol = rolRepository.saveAndFlush(rol);
        entrenador = new Usuario();
        entrenador.setRol(rol);
        entrenador.setNombres("Entrenador");
        entrenador.setApellidos("Pruebas");
        entrenador.setCorreo("coach-" + System.nanoTime() + "@example.test");
        entrenador.setContrasenaHash("hash-de-prueba-no-real");
        entrenador.setEstado("ACTIVO");
        entrenador = usuarioRepository.saveAndFlush(entrenador);

        elemento = new ElementoRevision();
        elemento.setNombre("Cinta de prueba");
        elemento.setTipo(TipoElementoRevision.MAQUINA);
        elemento.setUbicacion("Piso de pruebas");
        elemento.setActivo(true);
        elemento.setFechaCreacion(LocalDateTime.of(2026, 1, 1, 0, 0));
        elemento.setFechaActualizacion(elemento.getFechaCreacion());
        elemento = elementoRepository.saveAndFlush(elemento);
    }

    @Test
    void checklistConProblemaPersisteDetalleReporteEHistorialInicial() {
        var respuesta = checklistService.crear(new CrearChecklistRequest(List.of(
                new DetalleChecklistRequest(elemento.getIdElemento(),
                        ResultadoChecklist.CON_PROBLEMA, null,
                        "La cinta hace ruido", PrioridadIncidencia.ALTA)), null),
                entrenador.getCorreo());

        assertEquals(1, checklistRepository.count());
        assertEquals(1, detalleRepository.count());
        assertEquals(1, reporteRepository.count());
        assertEquals(1, historialRepository.count());
        assertEquals("Cinta de prueba", respuesta.detalles().getFirst().nombreElemento());
        assertEquals("PENDIENTE", respuesta.detalles().getFirst().incidencia().estado());
    }

    @Test
    void errorAlCrearReporteRevierteChecklistYDetalle() {
        doThrow(new IllegalStateException("Fallo inyectado de prueba"))
                .when(reporteRepositorySpy).save(any());

        assertThrows(IllegalStateException.class, () -> checklistService.crear(
                new CrearChecklistRequest(List.of(new DetalleChecklistRequest(
                        elemento.getIdElemento(), ResultadoChecklist.CON_PROBLEMA,
                        null, "Falla", PrioridadIncidencia.MEDIA)), null),
                entrenador.getCorreo()));

        assertEquals(0, checklistRepository.count());
        assertEquals(0, detalleRepository.count());
        assertEquals(0, reporteRepository.count());
        assertEquals(0, historialRepository.count());
    }
}
