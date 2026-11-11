package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.IngresoService;
import com.gimnasio.sistemagym.dto.PaginaIngresoMembresiaRespuesta;
import com.gimnasio.sistemagym.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(IngresoController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class IngresoControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private IngresoService ingresoService;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    @Test
    void soloRecepcionistaPuedeRegistrarIngresoDeCliente() throws Exception {
        String solicitud = "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"12345678\","
                + "\"idSocio\":42,\"nombre\":\"Ana\",\"apellidos\":\"Quispe\","
                + "\"metodoPago\":\"EFECTIVO\",\"claveIdempotencia\":"
                + "\"765c3b46-ec72-4a99-83fd-51f8d85a63b1\"}";
        mockMvc.perform(post("/api/ingresos")
                        .with(user("admin@example.test").roles("ADMINISTRADOR"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(solicitud))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/ingresos")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(solicitud))
                .andExpect(status().isCreated());
    }

    @Test
    void administradorYRecepcionistaPuedenConsultarDisponibilidad() throws Exception {
        when(ingresoService.isEsquemaPaseDiarioV2Habilitado()).thenReturn(false);
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado")
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(false));
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").value(false));
    }

    @Test
    void otrosRolesYAnonimosNoPuedenUsarLaRutaDeIngresos() throws Exception {
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingresos/pase-diario/habilitado")
                        .with(user("nutrition@example.test").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
    }

    @Test
    void historialDePasesDiariosSoloSeExponeATrasRolesDeRecepcion() throws Exception {
        when(ingresoService.listarPasesDiariosPorSocio(42L)).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/ingresos/pase-diario/socio/42")
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/ingresos/pase-diario/socio/42")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mockMvc.perform(get("/api/ingresos/pase-diario/socio/42")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void accesoMembresiaEsSoloRecepcionistaYConsultasSonParaRecepcionYAdministrador() throws Exception {
        String payload = "{\"idSocio\":42,\"claveIdempotencia\":\"765c3b46-ec72-4a99-83fd-51f8d85a63b1\"}";
        mockMvc.perform(post("/api/ingresos/membresia")
                        .with(user("admin@example.test").roles("ADMINISTRADOR"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingresos/membresia")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        when(ingresoService.evaluarIngresoMembresia(42L)).thenReturn(null);
        mockMvc.perform(get("/api/ingresos/socios/42/evaluacion")
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingresos/socios/42/evaluacion")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingresos/socios/42/evaluacion")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void directorioPaginadoDeHabilitadosEsSoloParaRecepcionYAdministracion() throws Exception {
        when(ingresoService.listarHabilitadosParaIngreso(
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq(""),
                org.mockito.ArgumentMatchers.eq(""), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(new PaginaIngresoMembresiaRespuesta(java.util.List.of(), 0, 20, 0, 0));
        mockMvc.perform(get("/api/ingresos/socios/habilitados")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.contenido").isArray());
        mockMvc.perform(get("/api/ingresos/socios/habilitados")
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ingresos/socios/habilitados")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/ingresos/socios/habilitados")
                        .with(user("nutrition@example.test").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
    }
}
