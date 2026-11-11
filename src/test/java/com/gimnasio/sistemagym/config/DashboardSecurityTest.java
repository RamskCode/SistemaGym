package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.controller.DashboardController;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.DashboardResumenService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class DashboardSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean DashboardResumenService dashboardService;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test
    void elResumenEsDeSoloLecturaParaAdministradorYRecepcionistaYNoParaOtrosRoles() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumen").with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/resumen").with(user("recepcion").roles("RECEPCIONISTA")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/otro").with(user("recepcion").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/resumen").with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/resumen").with(user("nutrition").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/resumen"))
                .andExpect(status().isUnauthorized());
        verify(dashboardService, times(2)).obtenerResumen();
    }
}
