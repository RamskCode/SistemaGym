package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.config.RelojConfig;
import com.gimnasio.sistemagym.dto.CrearChecklistRequest;
import com.gimnasio.sistemagym.dto.CambiarEstadoIncidenciaRequest;
import com.gimnasio.sistemagym.dto.DetalleChecklistRequest;
import com.gimnasio.sistemagym.entity.ElementoRevision;
import com.gimnasio.sistemagym.entity.EstadoIncidencia;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-checklist-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({ChecklistSalaService.class, ReporteIncidenciaService.class, RelojConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChecklistSalaJpaIntegrationTest {
    @Autowired ChecklistSalaService checklistService;
    @Autowired ReporteIncidenciaService reporteService;
    @Autowired ChecklistSalaRepository checklistRepository;
    @Autowired DetalleChecklistRepository detalleRepository;
    @Autowired ReporteIncidenciaRepository reporteRepository;
    @MockitoSpyBean HistorialReporteIncidenciaRepository historialRepository;
    @Autowired ElementoRevisionRepository elementoRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RolRepository rolRepository;

    @MockitoSpyBean
    ReporteIncidenciaRepository reporteRepositorySpy;

    private Usuario entrenador;
    private ElementoRevision elemento;

    @BeforeEach
    void prepararDatos() {
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
        entrenador.setCorreo("coach@example.test");
        entrenador.setContrasenaHash("hash-test-not-a-credential");
        entrenador.setEstado("ACTIVO");
        entrenador = usuarioRepository.saveAndFlush(entrenador);

        elemento = new ElementoRevision();
        elemento.setNombre("Cinta");
        elemento.setTipo(TipoElementoRevision.MAQUINA);
        elemento.setActivo(true);
        elemento.setFechaCreacion(LocalDateTime.of(2026, 1, 1, 0, 0));
        elemento.setFechaActualizacion(elemento.getFechaCreacion());
        elemento = elementoRepository.saveAndFlush(elemento);
    }

    @Test
    void persistenciaGuardaChecklistReporteEHistorial() {
        var response = checklistService.crear(new CrearChecklistRequest(List.of(
                new DetalleChecklistRequest(elemento.getIdElemento(),
                        ResultadoChecklist.CON_PROBLEMA, null, "Falla", PrioridadIncidencia.ALTA)),
                "observación general"), entrenador.getCorreo());

        assertEquals(1, checklistRepository.count());
        assertEquals(1, detalleRepository.count());
        assertEquals(1, reporteRepository.count());
        assertEquals(1, historialRepository.count());
        assertEquals("Cinta", response.detalles().getFirst().nombreElemento());
        assertEquals(java.time.ZoneOffset.ofHours(-5), response.fechaHora().getOffset());
    }

    @Test
    void excepcionAlCrearReporteRevierteLasFilasPreviamenteGuardadas() {
        doThrow(new IllegalStateException("Fallo simulado"))
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

    @Test
    void excepcionAlGuardarHistorialRevierteCambioDeEstado() {
        var creado = checklistService.crear(new CrearChecklistRequest(List.of(
                new DetalleChecklistRequest(elemento.getIdElemento(),
                        ResultadoChecklist.CON_PROBLEMA, null, "Falla", PrioridadIncidencia.ALTA)),
                null), entrenador.getCorreo());
        Long idReporte = creado.detalles().getFirst().incidencia().idReporte();
        org.mockito.Mockito.doThrow(new IllegalStateException("Fallo simulado"))
                .when(historialRepository).save(any());

        assertThrows(IllegalStateException.class, () -> reporteService.cambiarEstado(
                idReporte, new CambiarEstadoIncidenciaRequest(
                        EstadoIncidencia.EN_REVISION, "Inspección pendiente"),
                entrenador.getCorreo()));
        assertEquals(EstadoIncidencia.PENDIENTE,
                reporteRepository.findById(idReporte).orElseThrow().getEstado());
        assertEquals(1, historialRepository.count());
    }
}
