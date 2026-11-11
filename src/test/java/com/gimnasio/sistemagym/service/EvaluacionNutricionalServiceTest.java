package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gimnasio.sistemagym.config.RelojConfig;
import com.gimnasio.sistemagym.dto.EvaluacionNutricionalRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarEvaluacionNutricionalRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.EvaluacionNutricionalRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-evaluacion-registro-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({EvaluacionNutricionalService.class, ResumenNutricionService.class, RelojConfig.class})
class EvaluacionNutricionalServiceTest {

    @Autowired
    private EvaluacionNutricionalService service;

    @Autowired
    private ResumenNutricionService resumenNutricionService;

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
    void prepararDatos() {
        Rol rol = new Rol();
        rol.setNombre("NUTRICIONISTA");
        rol = rolRepository.saveAndFlush(rol);

        nutricionista = new Usuario();
        nutricionista.setRol(rol);
        nutricionista.setNombres("Nutricionista");
        nutricionista.setApellidos("Prueba");
        nutricionista.setCorreo("nutri@example.invalid");
        nutricionista.setContrasenaHash("hash-test-no-real");
        nutricionista = usuarioRepository.saveAndFlush(nutricionista);

        socio = new Socio();
        socio.setCodigoSocio("SOC-EVAL-001");
        socio.setNombres("Socio");
        socio.setApellidos("Prueba");
        socio = socioRepository.saveAndFlush(socio);
    }

    @Test
    void registraConUsuarioAutenticadoYObservacionesNulas() {
        EvaluacionNutricionalRespuesta respuesta = service.registrar(
                socio.getIdSocio(), request(null), nutricionista.getCorreo());

        assertEquals(nutricionista.getIdUsuario(), respuesta.idNutricionista());
        assertEquals(socio.getIdSocio(), respuesta.idSocio());
        assertNull(respuesta.observacionesSeguimiento());
        assertEquals(java.time.ZoneOffset.UTC, respuesta.fechaEvaluacion().atOffset(
                java.time.ZoneOffset.UTC).getOffset());
        assertEquals(1, evaluacionRepository.count());
        assertEquals(nutricionista.getIdUsuario(), evaluacionRepository.findAll().getFirst()
                .getNutricionista().getIdUsuario());
    }

    @Test
    void rechazaRegistroParaSocioInactivoSinPersistir() {
        socio.setEstado("INACTIVO");
        socioRepository.saveAndFlush(socio);

        assertThrows(ConflictoException.class,
                () -> service.registrar(socio.getIdSocio(), request(null),
                        nutricionista.getCorreo()));
        assertEquals(0, evaluacionRepository.count());
    }

    @Test
    void rechazaRegistroCuandoElSocioNoExiste() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.registrar(999999L, request("seguimiento"),
                        nutricionista.getCorreo()));
        assertEquals(0, evaluacionRepository.count());
    }

    @Test
    void devuelveHistorialConVariosNutricionistasOrdenadoPorFechaEId() {
        Usuario segundoNutricionista = nutricionista("otro-nutri@example.invalid");
        LocalDateTime fechaAnterior = LocalDateTime.of(2026, 10, 30, 8, 0);
        LocalDateTime fechaReciente = LocalDateTime.of(2026, 11, 2, 9, 0);

        var anterior = evaluacionRepository.saveAndFlush(
                evaluacion(nutricionista, fechaAnterior, "80.00"));
        var mismaFechaMenorId = evaluacionRepository.saveAndFlush(
                evaluacion(nutricionista, fechaReciente, "79.00"));
        var mismaFechaMayorId = evaluacionRepository.saveAndFlush(
                evaluacion(segundoNutricionista, fechaReciente, "78.00"));

        List<EvaluacionNutricionalRespuesta> historial = service.historial(socio.getIdSocio());

        assertEquals(3, historial.size());
        assertEquals(mismaFechaMayorId.getIdEvaluacion(), historial.get(0).idEvaluacion());
        assertEquals(segundoNutricionista.getIdUsuario(), historial.get(0).idNutricionista());
        assertEquals(mismaFechaMenorId.getIdEvaluacion(), historial.get(1).idEvaluacion());
        assertEquals(anterior.getIdEvaluacion(), historial.get(2).idEvaluacion());
    }

    @Test
    void devuelveHistorialVacioCuandoSocioExisteSinEvaluaciones() {
        assertEquals(List.of(), service.historial(socio.getIdSocio()));
    }

    @Test
    void socioInactivoPuedeConsultarSuHistorial() {
        socio.setEstado("INACTIVO");
        socioRepository.saveAndFlush(socio);
        evaluacionRepository.saveAndFlush(evaluacion(
                nutricionista, LocalDateTime.of(2026, 11, 2, 9, 0), "78.00"));

        assertEquals(1, service.historial(socio.getIdSocio()).size());
    }

    @Test
    void historialDeSocioInexistenteDevuelveRecursoNoEncontrado() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.historial(999999L));
    }

    @Test
    void resumenCuentaEvaluacionesYPersonasUnicasEnVentanasReales() {
        LocalDateTime ahora = LocalDateTime.now(java.time.Clock.systemUTC());
        Socio otroSocio = new Socio();
        otroSocio.setCodigoSocio("SOC-EVAL-002");
        otroSocio.setNombres("Otra");
        otroSocio.setApellidos("Persona");
        otroSocio.setEstado("ACTIVO");
        otroSocio = socioRepository.saveAndFlush(otroSocio);

        evaluacionRepository.saveAndFlush(evaluacion(nutricionista, ahora.minusDays(1), "78.00"));
        evaluacionRepository.saveAndFlush(evaluacion(nutricionista, ahora.minusDays(2), "77.00"));
        EvaluacionNutricional evaluacionOtraPersona = evaluacion(
                nutricionista, ahora.minusDays(3), "65.00");
        evaluacionOtraPersona.setSocio(otroSocio);
        evaluacionRepository.saveAndFlush(evaluacionOtraPersona);
        evaluacionRepository.saveAndFlush(evaluacion(
                nutricionista, ahora.minusDays(40), "76.00"));

        var resumen = resumenNutricionService.obtenerResumen();

        assertEquals(2, resumen.sociosDisponibles());
        assertEquals(3, resumen.evaluacionesEsteMes());
        assertEquals(2, resumen.sociosEvaluadosEsteMes());
        assertEquals(3, resumen.evaluacionesUltimosSieteDias());
        assertEquals(4, resumen.evaluacionesRecientes().size());
        assertEquals("SOC-EVAL-001", resumen.evaluacionesRecientes().get(0).codigoSocio());
    }

    private RegistrarEvaluacionNutricionalRequest request(String observaciones) {
        return new RegistrarEvaluacionNutricionalRequest(
                new BigDecimal("78.40"), new BigDecimal("1.72"),
                new BigDecimal("24.50"), new BigDecimal("32.10"),
                "Tres comidas al día", "Mejorar composición corporal",
                "Aumentar hidratación", observaciones);
    }

    private Usuario nutricionista(String correo) {
        Usuario otro = new Usuario();
        otro.setRol(nutricionista.getRol());
        otro.setNombres("Otro nutricionista");
        otro.setApellidos("Prueba");
        otro.setCorreo(correo);
        otro.setContrasenaHash("hash-test-no-real");
        return usuarioRepository.saveAndFlush(otro);
    }

    private EvaluacionNutricional evaluacion(Usuario autor, LocalDateTime fecha, String peso) {
        var evaluacion = new EvaluacionNutricional();
        evaluacion.setSocio(socio);
        evaluacion.setNutricionista(autor);
        evaluacion.setFechaEvaluacion(fecha);
        evaluacion.setPesoKg(new BigDecimal(peso));
        evaluacion.setTallaM(new BigDecimal("1.72"));
        evaluacion.setPorcentajeGrasa(new BigDecimal("24.50"));
        evaluacion.setMasaMuscularKg(new BigDecimal("32.10"));
        evaluacion.setHabitosAlimenticios("Hábitos");
        evaluacion.setObjetivosNutricionales("Objetivo");
        evaluacion.setRecomendacionesNutricionales("Recomendación");
        return evaluacion;
    }
}
