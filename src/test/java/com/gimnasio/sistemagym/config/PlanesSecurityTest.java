package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.controller.PlanController;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.PlanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class PlanesSecurityTest {

    private static final String CUERPO_PLAN = """
            {"nombre":"Mensual","descripcion":"Plan mensual",
             "precio":100.00,"duracionDias":30}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private PlanService planService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void listarPlanesPermiteSolicitudSinAutenticacion() throws Exception {
        when(planService.listarTodos()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/planes"))
                .andExpect(status().isOk());
    }

    @Test
    void consultarSociosRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/socios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registraSoloElUserDetailsServiceConfigurado() {
        assertEquals(1, applicationContext
                .getBeansOfType(UserDetailsService.class).size());
        assertFalse(applicationContext.containsBean("inMemoryUserDetailsManager"));
    }

    @Test
    void nutricionistaNoPuedeCrearPlan() throws Exception {
        mockMvc.perform(post("/api/planes")
                        .with(user("nutricionista").roles("NUTRICIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void recepcionistaNoPuedeCrearPlan() throws Exception {
        mockMvc.perform(post("/api/planes")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void nutricionistaNoPuedeActualizarPlan() throws Exception {
        mockMvc.perform(put("/api/planes/15")
                        .with(user("nutricionista").roles("NUTRICIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void recepcionistaNoPuedeActualizarPlan() throws Exception {
        mockMvc.perform(put("/api/planes/15")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void recepcionistaNoPuedeDesactivarPlan() throws Exception {
        mockMvc.perform(patch("/api/planes/15/desactivar")
                        .with(user("recepcionista").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void nutricionistaNoPuedeDesactivarPlan() throws Exception {
        mockMvc.perform(patch("/api/planes/15/desactivar")
                        .with(user("nutricionista").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService);
    }

    @Test
    void administradorPuedeCrearPlan() throws Exception {
        when(planService.registrar(any())).thenReturn(plan());

        mockMvc.perform(post("/api/planes")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPlan").value(15))
                .andExpect(jsonPath("$.nombre").value("Mensual"));
    }

    @Test
    void administradorPuedeActualizarPlan() throws Exception {
        when(planService.actualizar(eq(15L), any())).thenReturn(plan());

        mockMvc.perform(put("/api/planes/15")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content(CUERPO_PLAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPlan").value(15));
    }

    @Test
    void administradorPuedeDesactivarPlan() throws Exception {
        when(planService.desactivar(15L)).thenReturn(plan());

        mockMvc.perform(patch("/api/planes/15/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPlan").value(15));
    }

    private Plan plan() {
        Plan plan = new Plan();
        plan.setIdPlan(15L);
        plan.setNombre("Mensual");
        plan.setDescripcion("Plan mensual");
        plan.setPrecio(new BigDecimal("100.00"));
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        return plan;
    }
}
