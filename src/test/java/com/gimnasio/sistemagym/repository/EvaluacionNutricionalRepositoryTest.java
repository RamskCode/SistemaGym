package com.gimnasio.sistemagym.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-evaluaciones-nutricionales-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class EvaluacionNutricionalRepositoryTest {

    @Autowired
    private EvaluacionNutricionalRepository evaluacionRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Socio socio;
    private Usuario nutricionista;

    @BeforeEach
    void prepararRelaciones() {
        Rol rol = new Rol();
        rol.setNombre("NUTRICIONISTA");
        rol = rolRepository.saveAndFlush(rol);

        nutricionista = new Usuario();
        nutricionista.setRol(rol);
        nutricionista.setNombres("Nutricionista");
        nutricionista.setApellidos("De Prueba");
        nutricionista.setCorreo("nutricionista@example.invalid");
        nutricionista.setContrasenaHash("hash-test-no-real");
        nutricionista = usuarioRepository.saveAndFlush(nutricionista);

        socio = new Socio();
        socio.setCodigoSocio("SOC-NUTRI-001");
        socio.setNombres("Socio");
        socio.setApellidos("De Prueba");
        socio = socioRepository.saveAndFlush(socio);
    }

    @Test
    void persisteEvaluacionCompletaYAsociaciones() {
        EvaluacionNutricional original = evaluacion(
                LocalDateTime.of(2026, 11, 1, 9, 30), new BigDecimal("78.40"));
        original.setObservacionesSeguimiento("Revisar progreso en próxima visita.");

        EvaluacionNutricional guardada = evaluacionRepository.saveAndFlush(original);
        EvaluacionNutricional recuperada = evaluacionRepository.findById(
                guardada.getIdEvaluacion()).orElseThrow();

        assertNotNull(recuperada.getIdEvaluacion());
        assertEquals(socio.getIdSocio(), recuperada.getSocio().getIdSocio());
        assertEquals(nutricionista.getIdUsuario(), recuperada.getNutricionista().getIdUsuario());
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 30), recuperada.getFechaEvaluacion());
        assertEquals(new BigDecimal("78.40"), recuperada.getPesoKg());
        assertEquals(new BigDecimal("1.72"), recuperada.getTallaM());
        assertEquals(new BigDecimal("24.50"), recuperada.getPorcentajeGrasa());
        assertEquals(new BigDecimal("32.10"), recuperada.getMasaMuscularKg());
        assertEquals("Tres comidas al día.", recuperada.getHabitosAlimenticios());
        assertEquals("Mejorar composición corporal.", recuperada.getObjetivosNutricionales());
        assertEquals("Aumentar hidratación.", recuperada.getRecomendacionesNutricionales());
        assertEquals("Revisar progreso en próxima visita.",
                recuperada.getObservacionesSeguimiento());
    }

    @Test
    void persisteObservacionesDeSeguimientoNulas() {
        EvaluacionNutricional evaluacion = evaluacion(
                LocalDateTime.of(2026, 11, 2, 10, 0), new BigDecimal("77.80"));

        EvaluacionNutricional guardada = evaluacionRepository.saveAndFlush(evaluacion);

        assertNull(evaluacionRepository.findById(guardada.getIdEvaluacion())
                .orElseThrow().getObservacionesSeguimiento());
    }

    @Test
    void devuelveHistorialCompletoEnOrdenDescendentePorFechaYId() {
        LocalDateTime anterior = LocalDateTime.of(2026, 10, 30, 8, 0);
        LocalDateTime empate = LocalDateTime.of(2026, 11, 1, 9, 30);

        EvaluacionNutricional primera = evaluacion(anterior, new BigDecimal("80.00"));
        EvaluacionNutricional segunda = evaluacion(empate, new BigDecimal("79.00"));
        EvaluacionNutricional tercera = evaluacion(empate, new BigDecimal("78.00"));
        EvaluacionNutricional guardadaPrimera = evaluacionRepository.saveAndFlush(primera);
        EvaluacionNutricional guardadaSegunda = evaluacionRepository.saveAndFlush(segunda);
        EvaluacionNutricional guardadaTercera = evaluacionRepository.saveAndFlush(tercera);

        List<EvaluacionNutricional> historial = evaluacionRepository
                .findBySocio_IdSocioOrderByFechaEvaluacionDescIdEvaluacionDesc(
                        socio.getIdSocio());

        assertEquals(3, historial.size());
        assertEquals(guardadaTercera.getIdEvaluacion(), historial.get(0).getIdEvaluacion());
        assertEquals(guardadaSegunda.getIdEvaluacion(), historial.get(1).getIdEvaluacion());
        assertEquals(guardadaPrimera.getIdEvaluacion(), historial.get(2).getIdEvaluacion());
    }

    private EvaluacionNutricional evaluacion(LocalDateTime fecha, BigDecimal peso) {
        EvaluacionNutricional evaluacion = new EvaluacionNutricional();
        evaluacion.setSocio(socio);
        evaluacion.setNutricionista(nutricionista);
        evaluacion.setFechaEvaluacion(fecha);
        evaluacion.setPesoKg(peso);
        evaluacion.setTallaM(new BigDecimal("1.72"));
        evaluacion.setPorcentajeGrasa(new BigDecimal("24.50"));
        evaluacion.setMasaMuscularKg(new BigDecimal("32.10"));
        evaluacion.setHabitosAlimenticios("Tres comidas al día.");
        evaluacion.setObjetivosNutricionales("Mejorar composición corporal.");
        evaluacion.setRecomendacionesNutricionales("Aumentar hidratación.");
        return evaluacion;
    }
}
