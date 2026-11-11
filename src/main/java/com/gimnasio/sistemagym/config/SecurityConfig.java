package com.gimnasio.sistemagym.config;

import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.DispatcherType;

@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
            jwtAuthenticationFilterServletRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationEntryPoint jwtAuthenticationEntryPoint) {

        http
                .authenticationManager(authenticationManager)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .headers(headers -> headers.referrerPolicy(referrer ->
                        referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/api/auth/password-recovery/**").permitAll()
                        .requestMatchers("/api/auth/activation/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/resumen")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(HttpMethod.GET, "/api/dashboard/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/reportes/**").hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/setup/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/setup/administrator").permitAll()
                        .requestMatchers("/api/usuarios", "/api/usuarios/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/mi-cuenta", "/api/mi-cuenta/**")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/planes",
                                "/api/planes/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/planes",
                                "/api/planes/**"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/planes",
                                "/api/planes/**"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/planes",
                                "/api/planes/**"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/elementos-revision/activos")
                        .hasAnyRole("ENTRENADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/elementos-revision/{id}")
                        .hasAnyRole("ENTRENADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/elementos-revision",
                                "/api/elementos-revision/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST,
                                "/api/elementos-revision",
                                "/api/elementos-revision/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PUT,
                                "/api/elementos-revision",
                                "/api/elementos-revision/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/elementos-revision",
                                "/api/elementos-revision/**")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/checklists")
                        .hasRole("ENTRENADOR")
                        .requestMatchers(HttpMethod.GET, "/api/checklists/mios")
                        .hasRole("ENTRENADOR")
                        .requestMatchers(HttpMethod.GET, "/api/checklists")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/checklists/{id}")
                        .hasAnyRole("ENTRENADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/reportes-incidencias")
                        .hasRole("ENTRENADOR")
                        .requestMatchers(HttpMethod.GET, "/api/reportes-incidencias/mios")
                        .hasRole("ENTRENADOR")
                        .requestMatchers(HttpMethod.GET, "/api/reportes-incidencias")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/reportes-incidencias/{id}")
                        .hasAnyRole("ENTRENADOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/reportes-incidencias/{id}/estado")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/nutricion/resumen"
                        ).hasRole("NUTRICIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/socios/*/evaluaciones-nutricionales"
                        ).hasRole("NUTRICIONISTA")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/socios/*/evaluaciones-nutricionales"
                        ).hasRole("NUTRICIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/socios",
                                "/api/socios/**"
                        ).hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA", "NUTRICIONISTA")
                        .requestMatchers("/api/socios", "/api/socios/**")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/membresias",
                                "/api/membresias/**"
                        ).hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/membresias/procesar-ciclo-vida"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/membresias", "/api/membresias/**")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers("/api/operaciones-pago/**")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(HttpMethod.POST, "/api/ingresos")
                        .hasRole("RECEPCIONISTA")
                        .requestMatchers(HttpMethod.POST, "/api/ingresos/membresia")
                        .hasRole("RECEPCIONISTA")
                        .requestMatchers(HttpMethod.GET, "/api/ingresos/socios/habilitados")
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/ingresos/pase-diario/habilitado",
                                "/api/ingresos/pase-diario/socio/*",
                                "/api/ingresos/socios/*/evaluacion",
                                "/api/ingresos/socios/*/historial"
                        )
                        .hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers("/api/ingresos", "/api/ingresos/**")
                        .denyAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/configuraciones",
                                "/api/configuraciones/**"
                        ).hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(
                                "/api/configuraciones",
                                "/api/configuraciones/**"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/asistencias-laborales/personal-seleccionable"
                        ).hasAnyRole("ADMINISTRADOR", "RECEPCIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/asistencias-laborales/mis-asistencias"
                        ).hasAnyRole("RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/asistencias-laborales/empleado/*"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/asistencias-laborales/*/historial")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET,
                                "/api/asistencias-laborales/reportes/empleados/*")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/asistencias-laborales"
                        ).hasRole("ADMINISTRADOR")
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/asistencias-laborales/mi-entrada",
                                "/api/asistencias-laborales/mi-salida",
                                "/api/asistencias-laborales/entrada",
                                "/api/asistencias-laborales/salida"
                        ).hasRole("RECEPCIONISTA")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/asistencias-laborales/*/correccion")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/asistencias-laborales/*/anulacion")
                        .hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/**").denyAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
