package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.repository.SesionRefreshRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActuatorHealthSecurityTest.ActuatorRouteProbe.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        ActuatorHealthSecurityTest.ActuatorRouteProbe.class})
@TestPropertySource(properties = "app.actuator.health.public=true")
class ActuatorHealthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private SesionRefreshRepository refreshSessionRepository;

    @Test
    void soloGetHealthEsPublicoYDevuelveEstadoGeneral() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void otrasRutasActuatorNoSonPublicas() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void otrosMetodosNoSonPublicosEnHealth() throws Exception {
        mockMvc.perform(post("/actuator/health"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void elEstadoPublicoDeOnboardingConservaSuAutorizacion() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(false));
    }

    @RestController
    static class ActuatorRouteProbe {
        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }

        @GetMapping({"/actuator/env", "/actuator/health/db"})
        Map<String, String> internalRoute() {
            return Map.of("detail", "must not be public");
        }

        @PostMapping("/actuator/health")
        Map<String, String> healthPost() {
            return Map.of("status", "unexpected");
        }

        @GetMapping("/api/setup/status")
        Map<String, Boolean> onboardingStatus() {
            return Map.of("required", false);
        }
    }
}

@WebMvcTest(ActuatorHealthSecurityTest.ActuatorRouteProbe.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        ActuatorHealthSecurityTest.ActuatorRouteProbe.class})
@TestPropertySource(properties = "app.actuator.health.public=false")
class ActuatorHealthSecurityDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private SesionRefreshRepository refreshSessionRepository;

    @Test
    void healthNoEsPublicoSiLaPropiedadNoEstaHabilitada() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is4xxClientError());
    }
}
