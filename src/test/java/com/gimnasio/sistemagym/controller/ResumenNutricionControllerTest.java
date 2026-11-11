package com.gimnasio.sistemagym.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.dto.ResumenNutricionRespuesta;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.ResumenNutricionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ResumenNutricionController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class ResumenNutricionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumenNutricionService service;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void nutricionistaConsultaResumenConMetadatosMinimos() throws Exception {
        when(service.obtenerResumen()).thenReturn(new ResumenNutricionRespuesta(
                12, 8, 6, 3, List.of(new ResumenNutricionRespuesta.EvaluacionReciente(
                        71L, 23L, "SOC-0023", "Ana", "Prueba",
                        Instant.parse("2026-11-10T15:00:00Z")))));

        mockMvc.perform(get("/api/nutricion/resumen")
                        .with(user("nutri@example.invalid").roles("NUTRICIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sociosDisponibles").value(12))
                .andExpect(jsonPath("$.evaluacionesEsteMes").value(8))
                .andExpect(jsonPath("$.sociosEvaluadosEsteMes").value(6))
                .andExpect(jsonPath("$.evaluacionesUltimosSieteDias").value(3))
                .andExpect(jsonPath("$.evaluacionesRecientes[0].codigoSocio").value("SOC-0023"))
                .andExpect(jsonPath("$.evaluacionesRecientes[0].pesoKg").doesNotExist());

        verify(service).obtenerResumen();
    }

    @Test
    void otrosRolesNoPuedenConsultarElResumenNutricional() throws Exception {
        mockMvc.perform(get("/api/nutricion/resumen")
                        .with(user("recepcion@example.invalid").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }
}
