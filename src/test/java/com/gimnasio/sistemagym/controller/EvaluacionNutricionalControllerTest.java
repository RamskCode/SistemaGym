package com.gimnasio.sistemagym.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.dto.EvaluacionNutricionalRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarEvaluacionNutricionalRequest;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.EvaluacionNutricionalService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EvaluacionNutricionalController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class EvaluacionNutricionalControllerTest {

    private static final String CUERPO_VALIDO = """
            {
              "pesoKg": 78.40,
              "tallaM": 1.72,
              "porcentajeGrasa": 24.50,
              "masaMuscularKg": 32.10,
              "habitosAlimenticios": "Tres comidas al día",
              "objetivosNutricionales": "Mejorar composición corporal",
              "recomendacionesNutricionales": "Aumentar hidratación",
              "observacionesSeguimiento": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EvaluacionNutricionalService service;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void nutricionistaRegistraEvaluacionUsandoSuIdentidadAutenticada() throws Exception {
        when(service.registrar(eq(42L), any(RegistrarEvaluacionNutricionalRequest.class),
                eq("nutri@example.invalid"))).thenReturn(respuesta(9L, 42L, 17L));

        mockMvc.perform(post("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA"))
                        .contentType(APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEvaluacion").value(9))
                .andExpect(jsonPath("$.idSocio").value(42))
                .andExpect(jsonPath("$.idNutricionista").value(17))
                .andExpect(jsonPath("$.fechaEvaluacion").value("2026-11-04T12:00:00Z"))
                .andExpect(jsonPath("$.observacionesSeguimiento").doesNotExist());

        verify(service).registrar(eq(42L), any(RegistrarEvaluacionNutricionalRequest.class),
                eq("nutri@example.invalid"));
    }

    @Test
    void rechazaDatosFaltantesOFueraDeRango() throws Exception {
        mockMvc.perform(post("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"pesoKg":-1,"tallaM":1.72,"porcentajeGrasa":120,
                                 "masaMuscularKg":32.10,"habitosAlimenticios":" ",
                                 "objetivosNutricionales":"Objetivo",
                                 "recomendacionesNutricionales":"Recomendación"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rechazaSolicitudAnonima() throws Exception {
        mockMvc.perform(post("/api/socios/42/evaluaciones-nutricionales")
                        .contentType(APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void rechazaRolesDistintosDeNutricionista() throws Exception {
        mockMvc.perform(post("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void nutricionistaConsultaHistorialConDatosYAutor() throws Exception {
        when(service.historial(42L)).thenReturn(List.of(respuesta(9L, 42L, 17L)));

        mockMvc.perform(get("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEvaluacion").value(9))
                .andExpect(jsonPath("$[0].idSocio").value(42))
                .andExpect(jsonPath("$[0].idNutricionista").value(17))
                .andExpect(jsonPath("$[0].fechaEvaluacion").value("2026-11-04T12:00:00Z"))
                .andExpect(jsonPath("$[0].pesoKg").value(78.40))
                .andExpect(jsonPath("$[0].tallaM").value(1.72))
                .andExpect(jsonPath("$[0].porcentajeGrasa").value(24.50))
                .andExpect(jsonPath("$[0].masaMuscularKg").value(32.10))
                .andExpect(jsonPath("$[0].habitosAlimenticios").value("Hábitos"))
                .andExpect(jsonPath("$[0].objetivosNutricionales").value("Objetivo"))
                .andExpect(jsonPath("$[0].recomendacionesNutricionales")
                        .value("Recomendación"))
                .andExpect(jsonPath("$[0].observacionesSeguimiento").isEmpty());

        verify(service).historial(42L);
    }

    @Test
    void historialVacioSeDevuelveComoListaConEstadoOk() throws Exception {
        when(service.historial(42L)).thenReturn(List.of());

        mockMvc.perform(get("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void socioInexistenteDevuelveNotFound() throws Exception {
        when(service.historial(404L)).thenThrow(
                new RecursoNoEncontradoException("No existe un socio con el ID 404."));

        mockMvc.perform(get("/api/socios/404/evaluaciones-nutricionales")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA")))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialRechazaAnonimosYOtrosRoles() throws Exception {
        mockMvc.perform(get("/api/socios/42/evaluaciones-nutricionales"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/socios/42/evaluaciones-nutricionales")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    private EvaluacionNutricionalRespuesta respuesta(Long id, Long idSocio,
            Long idNutricionista) {
        return new EvaluacionNutricionalRespuesta(id, idSocio, idNutricionista,
                Instant.parse("2026-11-04T12:00:00Z"), new BigDecimal("78.40"),
                new BigDecimal("1.72"), new BigDecimal("24.50"),
                new BigDecimal("32.10"), "Hábitos", "Objetivo", "Recomendación", null);
    }
}
