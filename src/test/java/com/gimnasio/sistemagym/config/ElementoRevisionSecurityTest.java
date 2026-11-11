package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.controller.ElementoRevisionController;
import com.gimnasio.sistemagym.entity.TipoElementoRevision;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.ElementoRevisionService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ElementoRevisionController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class ElementoRevisionSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ElementoRevisionService service;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void entrenadorPuedeConsultarActivosPeroNoCatalogoCompleto() throws Exception {
        when(service.listarActivos()).thenReturn(List.of(elemento(true)));
        when(service.buscarActivoPorId(5L)).thenReturn(elemento(true));

        mockMvc.perform(get("/api/elementos-revision/activos")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].activo").value(true));

        mockMvc.perform(get("/api/elementos-revision/5")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        mockMvc.perform(get("/api/elementos-revision")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());

        verify(service).listarActivos();
        verify(service).buscarActivoPorId(5L);
    }

    @Test
    void administradorPuedeConsultarYAdministrarCatalogo() throws Exception {
        when(service.listarTodos()).thenReturn(List.of(elemento(true)));
        when(service.crear(any())).thenReturn(elemento(true));
        when(service.actualizar(eq(5L), any())).thenReturn(elemento(true));
        when(service.desactivar(5L)).thenReturn(elemento(false));

        mockMvc.perform(get("/api/elementos-revision")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idElemento").value(5));

        mockMvc.perform(post("/api/elementos-revision")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/elementos-revision/5")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/elementos-revision/5/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void otrosRolesNoAccedenYNoAutenticadoRecibe401() throws Exception {
        mockMvc.perform(get("/api/elementos-revision/activos")
                        .with(user("recepcionista").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/elementos-revision/activos")
                        .with(user("nutricionista").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/elementos-revision/activos"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void camposObligatoriosSeValidanAntesDeInvocarServicio() throws Exception {
        mockMvc.perform(post("/api/elementos-revision")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"tipo\":\"MAQUINA\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private String requestJson() {
        return """
                {"nombre":"Cinta","tipo":"MAQUINA",
                 "descripcion":"Cardio","ubicacion":"Piso 1"}
                """;
    }

    private com.gimnasio.sistemagym.dto.ElementoRevisionRespuesta elemento(boolean activo) {
        return new com.gimnasio.sistemagym.dto.ElementoRevisionRespuesta(
                5L, "Cinta", TipoElementoRevision.MAQUINA, "Cardio", "Piso 1",
                activo, LocalDateTime.of(2026, 1, 1, 10, 0),
                LocalDateTime.of(2026, 1, 1, 10, 0));
    }
}
