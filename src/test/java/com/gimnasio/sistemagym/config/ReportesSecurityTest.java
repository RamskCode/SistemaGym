package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.controller.ReportesController;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.ReportesService;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportesController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class ReportesSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ReportesService reportesService;
    @MockitoBean CustomUserDetailsService userDetailsService;
    @MockitoBean UsuarioRepository usuarioRepository;

    @Test
    void soloAdministradorPuedeConsultarReportes() throws Exception {
        mockMvc.perform(get("/api/reportes/resumen").param("desde", "2026-01-01").param("hasta", "2026-01-31")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reportes/resumen").param("desde", "2026-01-01").param("hasta", "2026-01-31")
                        .with(user("recep").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reportes/resumen").param("desde", "2026-01-01").param("hasta", "2026-01-31")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/reportes/resumen").param("desde", "2026-01-01").param("hasta", "2026-01-31"))
                .andExpect(status().isUnauthorized());
        verify(reportesService, times(1)).resumen(java.time.LocalDate.parse("2026-01-01"), java.time.LocalDate.parse("2026-01-31"), null);
    }
}
