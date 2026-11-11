package com.gimnasio.sistemagym.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.service.SocioService;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.service.RefreshSessionService;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.repository.SesionRefreshRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;

@WebMvcTest({AuthController.class, SocioController.class})
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class JwtAuthenticationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private FilterChainProxy filterChainProxy;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private RefreshSessionService refreshSessionService;

    @MockitoBean
    private SesionRefreshRepository refreshSessionRepository;

    @MockitoBean
    private SocioService socioService;

    @BeforeEach
    void credentialVersionsStartAtZero() {
        lenient().when(usuarioRepository.findCredentialVersionByCorreo(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(0L));
    }

    @Test
    void filtroJwtSoloSeRegistraEnLaCadenaDeSpringSecurity() {
        FilterRegistrationBean<?> servletRegistration = applicationContext.getBean(
                "jwtAuthenticationFilterServletRegistration", FilterRegistrationBean.class);

        assertFalse(servletRegistration.isEnabled());
        assertTrue(filterChainProxy.getFilters("/api/socios").stream()
                .anyMatch(JwtAuthenticationFilter.class::isInstance));
    }

    @Test
    void loginCorrectoEmiteBearerConRolDesdeAutenticacion() throws Exception {
        lenient().when(usuarioRepository.findCredentialVersionByCorreo("nutri@example.test"))
                .thenReturn(java.util.Optional.of(0L));
        var principal = User.withUsername("nutri@example.test")
                .password("{bcrypt}ignored")
                .roles("NUTRICIONISTA").build();
        Authentication authenticated = org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(authenticated);
        var rol = new Rol();
        rol.setNombre("NUTRICIONISTA");
        var usuario = new Usuario();
        usuario.setCorreo("nutri@example.test");
        usuario.setRol(rol);
        when(usuarioRepository.findByCorreoIgnoreCase("nutri@example.test"))
                .thenReturn(java.util.Optional.of(usuario));
        when(refreshSessionService.crear(org.mockito.ArgumentMatchers.same(usuario)))
                .thenReturn(new RefreshSessionService.Tokens(
                        tokenService.generar(principal), "opaque-refresh", "csrf-value", 900000,
                        java.time.Duration.ofHours(12)));

        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"correo\":\"nutri@example.test\",\"contrasena\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnMs").value(900000))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void refreshExigeCsrfYRotacionEmiteCookiesHttpOnlyYSameSite() throws Exception {
        var rotated = new RefreshSessionService.Tokens("new-access", "new-refresh", "new-csrf",
                900_000, java.time.Duration.ofHours(6));
        when(refreshSessionService.renovar("old-refresh", "csrf-value")).thenReturn(rotated);

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("SG_REFRESH", "old-refresh"),
                                new jakarta.servlet.http.Cookie("SG_CSRF", "csrf-value"))
                        .header("X-CSRF-Token", "csrf-value"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-access"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders("Set-Cookie").stream()
                                .anyMatch(value -> value.contains("SG_REFRESH=new-refresh")
                                        && value.contains("HttpOnly") && value.contains("SameSite=Strict"))));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("SG_REFRESH", "old-refresh"),
                                new jakarta.servlet.http.Cookie("SG_CSRF", "cookie-csrf"))
                        .header("X-CSRF-Token", "different-header"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(refreshSessionService,
                org.mockito.Mockito.times(1)).renovar("old-refresh", "csrf-value");
    }

    @Test
    void logoutRevocaYEliminaAmbasCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("SG_REFRESH", "refresh-value"),
                                new jakarta.servlet.http.Cookie("SG_CSRF", "csrf-value"))
                        .header("X-CSRF-Token", "csrf-value"))
                .andExpect(status().isNoContent())
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getHeaders("Set-Cookie").stream()
                                .anyMatch(value -> value.startsWith("SG_REFRESH=") && value.contains("Max-Age=0"))))
                .andExpect(result -> org.mockito.Mockito.verify(refreshSessionService)
                        .revocar("refresh-value", "csrf-value"));
    }

    @Test
    void rechazaLoginInvalidoYEntradaProtegidaSinToken() throws Exception {
        when(authenticationManager.authenticate(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new org.springframework.security.authentication
                        .BadCredentialsException("invalid"));
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"correo\":\"nutri@example.test\",\"contrasena\":\"bad\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenValidoCargaAutoridadesConfiablesYTokenAlteradoSeRechaza() throws Exception {
        var principal = User.withUsername("nutri@example.test")
                .password("unused").roles("NUTRICIONISTA").build();
        when(userDetailsService.loadUserByUsername("nutri@example.test"))
                .thenReturn(principal);
        String valid = tokenService.generar(principal);
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + valid))
                .andExpect(status().isForbidden());

        String tampered = valid.substring(0, valid.length() - 2) + "aa";
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/usuarios")
                        .header("Authorization", "Bearer definitely-not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtAnteriorAlCambioUsaElRolActualCargadoDesdeElServicio() throws Exception {
        var entrenadorAlEmitirToken = User.withUsername("persona@example.test")
                .password("unused").roles("ENTRENADOR").build();
        var recepcionistaActual = User.withUsername("persona@example.test")
                .password("unused").roles("RECEPCIONISTA").build();
        String tokenEmitidoComoEntrenador = tokenService.generar(entrenadorAlEmitirToken);

        when(userDetailsService.loadUserByUsername("persona@example.test"))
                .thenReturn(recepcionistaActual, entrenadorAlEmitirToken);
        when(socioService.listarTodos()).thenReturn(java.util.List.of());

        // El mismo JWT adopta las autoridades actuales, no las del claim al emitirlo.
        mockMvc.perform(get("/api/socios")
                        .header("Authorization", "Bearer " + tokenEmitidoComoEntrenador))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/socios")
                        .header("Authorization", "Bearer " + tokenEmitidoComoEntrenador))
                .andExpect(status().isForbidden());
    }

    @Test
    void accessTokenDeSesionRevocadaSeRechazaInmediatamente() throws Exception {
        var receptionist = User.withUsername("reception@example.test")
                .password("unused").roles("RECEPCIONISTA").build();
        when(userDetailsService.loadUserByUsername("reception@example.test"))
                .thenReturn(receptionist);
        when(usuarioRepository.findCredentialVersionByCorreo("reception@example.test"))
                .thenReturn(java.util.Optional.of(0L));
        String token = tokenService.generar(receptionist, 0L, "session-id-17");
        when(refreshSessionRepository.existsByIdAndExpiraEnAfter(
                org.mockito.ArgumentMatchers.eq("session-id-17"),
                org.mockito.ArgumentMatchers.any(java.time.Instant.class)))
                .thenReturn(false, true);
        when(socioService.listarTodos()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/socios").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/socios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void jwtRealDeEntrenadorRecibe403YAdministradorRecibe200EnSocios()
            throws Exception {
        var entrenador = User.withUsername("trainer@example.test")
                .password("unused").roles("ENTRENADOR").build();
        var administrador = User.withUsername("admin@example.test")
                .password("unused").roles("ADMINISTRADOR").build();
        var recepcionista = User.withUsername("reception@example.test")
                .password("unused").roles("RECEPCIONISTA").build();
        when(userDetailsService.loadUserByUsername("trainer@example.test"))
                .thenReturn(entrenador);
        when(userDetailsService.loadUserByUsername("admin@example.test"))
                .thenReturn(administrador);
        when(userDetailsService.loadUserByUsername("reception@example.test"))
                .thenReturn(recepcionista);
        when(socioService.listarTodos()).thenReturn(java.util.List.of());

        String tokenEntrenador = tokenService.generar(entrenador);
        String tokenAdministrador = tokenService.generar(administrador);
        String tokenRecepcionista = tokenService.generar(recepcionista);

        mockMvc.perform(get("/api/socios")
                        .header("Authorization", "Bearer " + tokenEntrenador))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/socios")
                        .header("Authorization", "Bearer " + tokenRecepcionista))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/socios")
                        .header("Authorization", "Bearer " + tokenAdministrador))
                .andExpect(status().isOk());
    }

    @Test
    void errorDispatchEstaPermitidoPeroSolicitudNormalAErrorSigueProtegida()
            throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 403);
                    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI,
                            "/api/socios");
                    return request;
                }))
                .andExpect(result -> assertNotEquals(401,
                        result.getResponse().getStatus()));
    }
}
