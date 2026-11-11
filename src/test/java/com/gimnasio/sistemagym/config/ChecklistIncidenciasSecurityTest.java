package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.controller.ChecklistSalaController;
import com.gimnasio.sistemagym.controller.ReporteIncidenciaController;
import com.gimnasio.sistemagym.service.ChecklistSalaService;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.ReporteIncidenciaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ChecklistSalaController.class, ReporteIncidenciaController.class})
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class ChecklistIncidenciasSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ChecklistSalaService checklistService;
    @MockitoBean ReporteIncidenciaService reporteService;
    @MockitoBean CustomUserDetailsService userDetailsService;

    @Test
    void soloEntrenadorPuedeCrearYConsultarSusListas() throws Exception {
        mockMvc.perform(post("/api/checklists")
                        .with(user("coach").roles("ENTRENADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"detalles\":[{\"idElemento\":1,\"resultado\":\"CORRECTO\"}]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/checklists/mios")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reportes-incidencias")
                        .with(user("coach").roles("ENTRENADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idElemento\":1,\"descripcion\":\"Falla\",\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/reportes-incidencias/mios")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isOk());
    }

    @Test
    void administradorPuedeListarPeroNoCrear() throws Exception {
        mockMvc.perform(get("/api/checklists")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes-incidencias")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/checklists")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"detalles\":[{\"idElemento\":1,\"resultado\":\"CORRECTO\"}]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_REVISION\",\"observacion\":\"Revisar\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/reportes-incidencias")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idElemento\":1,\"descripcion\":\"Falla\",\"prioridad\":\"ALTA\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void recepcionistaNutricionistaYSolicitudesAnonimasNoAcceden() throws Exception {
        for (String role : new String[]{"RECEPCIONISTA", "NUTRICIONISTA"}) {
            mockMvc.perform(get("/api/checklists/mios")
                            .with(user("user").roles(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/reportes-incidencias/mios")
                            .with(user("user").roles(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/checklists/mios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reportes-incidencias/mios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_REVISION\",\"observacion\":\"Revisar\"}"))
                .andExpect(status().isUnauthorized());
        for (String role : new String[]{"ENTRENADOR", "RECEPCIONISTA", "NUTRICIONISTA"}) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/reportes-incidencias/1/estado")
                            .with(user("user").roles(role))
                            .contentType(APPLICATION_JSON)
                            .content("{\"estado\":\"EN_REVISION\",\"observacion\":\"Revisar\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(checklistService, reporteService);
    }

    @Test
    void observacionObligatoriaYEstadoValidoSeValidanAntesDelServicio() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_REVISION\",\"observacion\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"CERRADO\",\"observacion\":\"Revisar\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_REVISION\",\"observacion\":\"\"}"))
                .andExpect(status().isBadRequest());
        String observacionLarga = "x".repeat(2001);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/reportes-incidencias/1/estado")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"estado\":\"EN_REVISION\",\"observacion\":\""
                                + observacionLarga + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reporteService);
    }
}
