package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.dto.OperacionPagoIdempotenteRespuesta;
import com.gimnasio.sistemagym.entity.EstadoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.OperacionPagoIdempotenteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperacionPagoIdempotenteController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class OperacionPagoIdempotenteControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private OperacionPagoIdempotenteService service;
    @MockitoBean private CustomUserDetailsService userDetailsService;

    @Test
    void noAutenticadoNoPuedeConsultar() throws Exception {
        mockMvc.perform(get("/api/operaciones-pago/opaque"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/operaciones-pago/por-clave/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administradorYRecepcionistaAutenticadosPuedenConsultar() throws Exception {
        String key = "00000000-0000-0000-0000-000000000000";
        when(service.consultarPorClave(eq("admin@example.test"), eq(key))).thenReturn(pendiente("op-admin"));
        when(service.consultarPorClave(eq("recepcion@example.test"), eq(key))).thenReturn(pendiente("op-recepcion"));
        when(service.consultar("admin@example.test", "opaque"))
                .thenReturn(pendiente("opaque"));
        when(service.consultar("recepcion@example.test", "opaque"))
                .thenReturn(pendiente("opaque"));

        mockMvc.perform(get("/api/operaciones-pago/opaque")
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDING"));
        mockMvc.perform(get("/api/operaciones-pago/por-clave/" + key)
                        .with(user("admin@example.test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDING"));
        mockMvc.perform(get("/api/operaciones-pago/por-clave/" + key)
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDING"));
        mockMvc.perform(get("/api/operaciones-pago/opaque")
                        .with(user("recepcion@example.test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDING"));
    }

    @Test
    void otrosRolesNoPuedenConsultarOperacionesDePago() throws Exception {
        mockMvc.perform(get("/api/operaciones-pago/opaque")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operaciones-pago/por-clave/00000000-0000-0000-0000-000000000000")
                        .with(user("coach@example.test").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operaciones-pago/opaque")
                        .with(user("nutrition@example.test").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/operaciones-pago/por-clave/00000000-0000-0000-0000-000000000000")
                        .with(user("nutrition@example.test").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
    }

    private static OperacionPagoIdempotenteRespuesta pendiente(String id) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 8, 12, 0);
        return new OperacionPagoIdempotenteRespuesta(id,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL,
                EstadoOperacionPagoIdempotente.PENDING, now, now, null, null, null, null, null, null);
    }
}
