package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.dto.ReporteFilaRespuesta;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.ReportesService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistorialIngresosRecepcionController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class HistorialIngresosRecepcionControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private ReportesService reportesService;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    @Test
    void recepcionistaConsultaIngresosPaginadosYFiltradosEnSoloLectura() throws Exception {
        var row = new ReporteFilaRespuesta(9L, "VISITANTE", "Visitante de prueba",
                "Pase diario", "REGISTRADO", null, LocalDateTime.parse("2026-11-12T10:00:00"),
                "CE · ••••AB12", "PASE_DIARIO", "PRIMER_INGRESO", "Maricarmen Santos");
        when(reportesService.historialIngresos(LocalDate.parse("2026-11-12"),
                LocalDate.parse("2026-11-12"), 0, 25, "visitante", "PASE_DIARIO"))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/ingresos/historial")
                        .with(user("recepcion@test").roles("RECEPCIONISTA"))
                        .param("desde", "2026-11-12").param("hasta", "2026-11-12")
                        .param("buscar", "visitante").param("modalidad", "PASE_DIARIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].persona").value("Visitante de prueba"))
                .andExpect(jsonPath("$.contenido[0].documentoProtegido").value("CE · ••••AB12"))
                .andExpect(jsonPath("$.contenido[0].modalidad").value("PASE_DIARIO"))
                .andExpect(jsonPath("$.totalElementos").value(1));

        mockMvc.perform(post("/api/ingresos/historial")
                        .with(user("recepcion@test").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingresos/historial")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("desde", "2026-11-12").param("hasta", "2026-11-12"))
                .andExpect(status().isForbidden());
        verify(reportesService).historialIngresos(LocalDate.parse("2026-11-12"),
                LocalDate.parse("2026-11-12"), 0, 25, "visitante", "PASE_DIARIO");
    }
}
