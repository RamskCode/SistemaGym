package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.dto.MembresiaRespuesta;
import com.gimnasio.sistemagym.dto.PlanRespuesta;
import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.MembresiaService;
import com.gimnasio.sistemagym.service.MembresiaIdempotenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MembresiaController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class MembresiaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MembresiaService membresiaService;

    @MockitoBean
    private MembresiaIdempotenteService membresiaIdempotenteService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void listarMembresiasDevuelveDtoConSocioYPlan() throws Exception {
        when(membresiaService.listarTodas()).thenReturn(List.of(respuesta()));

        mockMvc.perform(get("/api/membresias")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idMembresia").value(1))
                .andExpect(jsonPath("$[0].socio.nombres").value("Ana"))
                .andExpect(jsonPath("$[0].plan.nombre").value("Mensual"))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$[0].socio.hibernateLazyInitializer").doesNotExist())
                .andExpect(jsonPath("$[0].plan.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void buscarPorIdDevuelveDto() throws Exception {
        when(membresiaService.buscarPorId(1L)).thenReturn(respuesta());

        mockMvc.perform(get("/api/membresias/1")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idMembresia").value(1))
                .andExpect(jsonPath("$.socio.codigoSocio").value("SOC-0015"))
                .andExpect(jsonPath("$.plan.idPlan").value(15));
    }

    @Test
    void buscarPorIdInexistenteDevuelve404() throws Exception {
        when(membresiaService.buscarPorId(99L))
                .thenThrow(new RecursoNoEncontradoException(
                        "No se encontro la membresia con ID: 99"));

        mockMvc.perform(get("/api/membresias/99")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensaje")
                        .value("No se encontro la membresia con ID: 99"));
    }

    @Test
    void buscarPorSocioDevuelveDtos() throws Exception {
        when(membresiaService.buscarPorSocio(7L)).thenReturn(List.of(respuesta()));

        mockMvc.perform(get("/api/membresias/socio/7")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idMembresia").value(1))
                .andExpect(jsonPath("$[0].socio.idSocio").value(7));
    }

    private MembresiaRespuesta respuesta() {
        SocioRespuesta socio = new SocioRespuesta(
                7L, "SOC-0015", "12345678", "DNI", "12345678", "Ana", "Prueba",
                LocalDate.of(1990, 1, 1), "999999999",
                "ana@example.test", "ACTIVO", LocalDateTime.of(2026, 1, 1, 9, 0)
        );
        PlanRespuesta plan = new PlanRespuesta(
                15L, "Mensual", "Plan mensual", new BigDecimal("100.00"),
                30, "ACTIVO", LocalDateTime.of(2026, 1, 1, 9, 0)
        );

        return new MembresiaRespuesta(
                1L, socio, plan, new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 1, 6), "ACTIVA", null,
                LocalDateTime.of(2026, 1, 1, 9, 0)
        );
    }
}
