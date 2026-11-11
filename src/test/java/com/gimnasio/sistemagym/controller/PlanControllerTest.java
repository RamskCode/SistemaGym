package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.dto.RegistrarPlanRequest;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.PlanService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void listarPlanesDevuelveDtoSinExponerEntidad() throws Exception {
        when(planService.listarTodos()).thenReturn(List.of(plan()));

        mockMvc.perform(get("/api/planes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idPlan").value(15))
                .andExpect(jsonPath("$[0].nombre").value("Mensual"))
                .andExpect(jsonPath("$[0].precio").value(100.00))
                .andExpect(jsonPath("$[0].duracionDias").value(30))
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[0].hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void buscarPorIdDevuelveDto() throws Exception {
        when(planService.buscarPorId(15L)).thenReturn(Optional.of(plan()));

        mockMvc.perform(get("/api/planes/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPlan").value(15))
                .andExpect(jsonPath("$.nombre").value("Mensual"))
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void buscarPorIdInexistenteDevuelve404() throws Exception {
        when(planService.buscarPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/planes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Plan no encontrado"));
    }

    @Test
    void registrarPlanIgnoraIdPlanEnviadoPorCliente() throws Exception {
        when(planService.registrar(any())).thenReturn(plan());

        mockMvc.perform(post("/api/planes")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"idPlan":9999,"nombre":"Mensual",
                                 "descripcion":"Plan mensual",
                                 "precio":100.00,"duracionDias":30}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPlan").value(15));

        ArgumentCaptor<RegistrarPlanRequest> captor =
                ArgumentCaptor.forClass(RegistrarPlanRequest.class);
        verify(planService).registrar(captor.capture());

        assertEquals("Mensual", captor.getValue().nombre());
        assertEquals(new BigDecimal("100.00"), captor.getValue().precio());
        assertEquals(30, captor.getValue().duracionDias());
    }

    @Test
    void registrarPlanSinNombreDevuelve400() throws Exception {
        mockMvc.perform(post("/api/planes")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"precio":100.00,"duracionDias":30}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actualizarPlanDevuelveDto() throws Exception {
        when(planService.actualizar(eq(15L), any())).thenReturn(plan());

        mockMvc.perform(put("/api/planes/15")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombre":"Mensual","descripcion":"Plan mensual",
                                 "precio":100.00,"duracionDias":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPlan").value(15))
                .andExpect(jsonPath("$.nombre").value("Mensual"));
    }

    @Test
    void desactivarPlanDevuelveDto() throws Exception {
        Plan desactivado = plan();
        desactivado.setEstado("INACTIVO");
        when(planService.desactivar(15L)).thenReturn(desactivado);

        mockMvc.perform(patch("/api/planes/15/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPlan").value(15))
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
    }

    private Plan plan() {
        Plan plan = new Plan();
        plan.setIdPlan(15L);
        plan.setNombre("Mensual");
        plan.setDescripcion("Plan mensual");
        plan.setPrecio(new BigDecimal("100.00"));
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan.setFechaCreacion(LocalDateTime.of(2026, 1, 1, 10, 0));
        return plan;
    }
}
