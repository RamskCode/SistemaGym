package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.service.JwtTokenService;
import com.gimnasio.sistemagym.dto.ActualizarUsuarioRequest;
import com.gimnasio.sistemagym.dto.ActualizarMiCuentaRequest;
import com.gimnasio.sistemagym.dto.CambiarContrasenaUsuarioRequest;
import com.gimnasio.sistemagym.dto.CambiarMiContrasenaRequest;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.dto.UsuarioRespuesta;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.UsuarioAdminService;
import com.gimnasio.sistemagym.service.UsuarioInvitacionService;
import com.gimnasio.sistemagym.controller.ActivacionCuentaController;
import com.gimnasio.sistemagym.controller.MiCuentaController;
import com.gimnasio.sistemagym.dto.MiCuentaRespuesta;
import com.gimnasio.sistemagym.dto.CredencialesInicialesRespuesta;
import com.gimnasio.sistemagym.service.MiCuentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({UsuarioController.class, ActivacionCuentaController.class, MiCuentaController.class})
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class UsuarioControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private UsuarioAdminService usuarioAdminService;

    @MockitoBean
    private UsuarioInvitacionService invitaciones;

    @MockitoBean
    private MiCuentaService miCuentaService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void soloRegistraElUserDetailsServiceDeMySqlSinFallbackEnMemoria() {
        assertEquals(1, applicationContext
                .getBeansOfType(UserDetailsService.class).size());
        assertFalse(applicationContext.containsBean("inMemoryUserDetailsManager"));
    }

    @Test
    void miCuentaPermiteConsultarYActualizarSoloLaIdentidadAutenticadaEnLosCuatroRoles()
            throws Exception {
        when(miCuentaService.consultar("self@example.test")).thenReturn(
                new MiCuentaRespuesta("Ana", "Prueba", "self@example.test",
                        "ADMINISTRADOR", null, null));
        for (String role : List.of("ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")) {
            mockMvc.perform(get("/api/mi-cuenta")
                            .with(user("self@example.test").roles(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombres").value("Ana"))
                    .andExpect(jsonPath("$.correo").value("self@example.test"))
                    .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                    .andExpect(jsonPath("$.idUsuario").doesNotExist());

            mockMvc.perform(patch("/api/mi-cuenta")
                            .with(user("self@example.test").roles(role))
                            .contentType(APPLICATION_JSON)
                            .content("{\"nombres\":\"Ana nueva\"}"))
                    .andExpect(status().isOk());
        }
        verify(miCuentaService, times(4)).consultar("self@example.test");
        verify(miCuentaService, times(4)).actualizar(
                eq("self@example.test"), any(ActualizarMiCuentaRequest.class));
    }

    @Test
    void miCuentaRechazaCamposProtegidosEIdentificadoresEnPatch() throws Exception {
        for (String body : List.of(
                "{\"rol\":\"ADMINISTRADOR\"}",
                "{\"correo\":\"other@example.test\"}",
                "{\"estado\":\"INACTIVO\"}",
                "{\"idUsuario\":99,\"nombres\":\"Cambio\"}",
                "{\"permisos\":[\"ADMINISTRADOR\"]}")) {
            mockMvc.perform(patch("/api/mi-cuenta")
                            .with(user("self@example.test").roles("ENTRENADOR"))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(miCuentaService);
    }

    @Test
    void miCuentaRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/api/mi-cuenta")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/mi-cuenta/contrasena")
                        .contentType(APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"x\",\"contrasenaNueva\":\"y\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(miCuentaService);
    }

    @Test
    void cambioDeContrasenaPersonalEsSeparadoYRevocaSesionesEnServicio() throws Exception {
        mockMvc.perform(patch("/api/mi-cuenta/contrasena")
                        .with(user("self@example.test").roles("NUTRICIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"CurrentStrong!42\",\"contrasenaNueva\":\"DifferentStrong!62\"}"))
                .andExpect(status().isNoContent());
        verify(miCuentaService).cambiarContrasena(eq("self@example.test"),
                any(CambiarMiContrasenaRequest.class));
    }

    @Test
    void losCuatroRolesPuedenCambiarSoloLaContrasenaDeSuIdentidadAutenticada()
            throws Exception {
        for (String role : List.of("ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA")) {
            mockMvc.perform(patch("/api/mi-cuenta/contrasena")
                            .with(user("self@example.test").roles(role))
                            .contentType(APPLICATION_JSON)
                            .content("{\"contrasenaActual\":\"CurrentStrong!42\",\"contrasenaNueva\":\"DifferentStrong!62\"}"))
                    .andExpect(status().isNoContent());
        }
        verify(miCuentaService, times(4)).cambiarContrasena(
                eq("self@example.test"), any(CambiarMiContrasenaRequest.class));
    }

    @Test
    void miCuentaNoSeAmpliaARolesNoPermitidos() throws Exception {
        mockMvc.perform(get("/api/mi-cuenta").with(user("other@example.test").roles("AUDITOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/mi-cuenta")
                        .with(user("other@example.test").roles("AUDITOR"))
                        .contentType(APPLICATION_JSON).content("{\"nombres\":\"Cambio\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(miCuentaService);
    }

    @Test
    void administradorPuedeListarUsuarios() throws Exception {
        when(usuarioAdminService.listarTodos()).thenReturn(List.of());

        mockMvc.perform(get("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());

        verify(usuarioAdminService).listarTodos();
    }

    @Test
    void endpointsDeActivacionSonPublicosPeroLasInvitacionesSiguenProtegidas() throws Exception {
        when(invitaciones.isTokenValid("fake-token")).thenReturn(true);
        mockMvc.perform(post("/api/auth/activation/validate")
                        .contentType(APPLICATION_JSON).content("{\"token\":\"fake-token\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/usuarios/21/invitacion")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/usuarios/21/restablecer-acceso")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        verify(invitaciones, never()).sendInvitation(anyLong());
        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void respuestaDeUsuarioNoIncluyeHashNiContrasena() throws Exception {
        when(usuarioAdminService.buscarPorId(12L)).thenReturn(
                new UsuarioRespuesta(12L, "Ana", "Prueba", "ana@example.com",
                        "NUTRICIONISTA", "ACTIVO", null));

        mockMvc.perform(get("/api/usuarios/12")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana@example.com"))
                .andExpect(jsonPath("$.contrasena_hash").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void devuelve404ParaUsuarioInexistente() throws Exception {
        when(usuarioAdminService.buscarPorId(88L))
                .thenThrow(new RecursoNoEncontradoException(
                        "No existe un usuario con el ID 88."));

        mockMvc.perform(get("/api/usuarios/88")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaSolicitudInvalidaAntesDeInvocarServicio() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombres\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void rechazaAltaSinDocumentoAntesDeInvocarServicio() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ana@example.com","rol":"NUTRICIONISTA"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void altaAdminDevuelveContrasenaInicialUnaSolaVezYNoHash() throws Exception {
        when(usuarioAdminService.crear(any(CrearUsuarioRequest.class))).thenReturn(
                new CredencialesInicialesRespuesta(
                        new UsuarioRespuesta(21L, "Ana", "Prueba", "ana@example.com",
                                "NUTRICIONISTA", "ACTIVO", null, TipoDocumento.CE, "12345"),
                        "R4nd0m-Initial!Credential"));

        mockMvc.perform(post("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                  "correo":"ana@example.com",
                                  "rol":"NUTRICIONISTA",
                                  "tipoDocumento":"CE","numeroDocumento":"12345"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.idUsuario").value(21))
                .andExpect(jsonPath("$.usuario.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contrasenaInicial").value("R4nd0m-Initial!Credential"))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.contrasena_hash").doesNotExist())
                .andExpect(jsonPath("$.usuario.tipoDocumento").value("CE"))
                .andExpect(jsonPath("$.usuario.numeroDocumento").value("12345"));
        verifyNoInteractions(invitaciones);
    }

    @Test
    void rechazaDocumentoParcialEnAltaYActualizacion() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ana@example.com",
                                 "contrasena":"StrongTestPassword!42",
                                 "rol":"NUTRICIONISTA",
                                 "tipoDocumento":"DNI"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/usuarios/21")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ana@example.com",
                                 "tipoDocumento":"DNI"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void validacionRestRechazaFormatosDniYCeFueraDeRegla() throws Exception {
        for (String[] documento : new String[][] {
                {"DNI", "1234567"}, {"DNI", "123456789"}, {"DNI", "1234AB78"},
                {"CE", "A".repeat(13)}, {"CE", "AB C"}, {"CE", "AB-C"}, {"CE", "AB!C"}
        }) {
            String body = "{\"nombres\":\"Ana\",\"apellidos\":\"Prueba\","
                    + "\"correo\":\"ana@example.com\",\"rol\":\"NUTRICIONISTA\","
                    + "\"tipoDocumento\":\"" + documento[0] + "\",\"numeroDocumento\":\""
                    + documento[1] + "\"}";
            mockMvc.perform(post("/api/usuarios")
                            .with(user("admin").roles("ADMINISTRADOR"))
                            .contentType(APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void correoDuplicadoSeDevuelveComoConflictoHttp() throws Exception {
        when(usuarioAdminService.crear(any(CrearUsuarioRequest.class)))
                .thenThrow(new ConflictoException(
                        "Ya existe un usuario con ese correo."));

        mockMvc.perform(post("/api/usuarios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ana@example.com",
                                 "rol":"NUTRICIONISTA",
                                 "tipoDocumento":"DNI","numeroDocumento":"12345678"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void correoDuplicadoEnActualizacionDevuelve409ConMensajeSeguro() throws Exception {
        when(usuarioAdminService.actualizar(eq(21L), any(ActualizarUsuarioRequest.class)))
                .thenThrow(new ConflictoException(
                        "Ya existe un usuario con ese correo."));

        mockMvc.perform(put("/api/usuarios/21")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ocupado@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje")
                        .value("Ya existe un usuario con ese correo."));
    }

    @Test
    void contrasenaDebilOVaciaAlCambiarlaDevuelve400() throws Exception {
        for (String body : List.of(
                "{\"contrasena\":\"\"}",
                "{\"contrasena\":\"weak\"}")) {
            mockMvc.perform(patch("/api/usuarios/21/contrasena")
                            .with(user("admin").roles("ADMINISTRADOR"))
                            .contentType(APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores").isArray());
        }

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void rolInexistenteAlCambiarloDevuelve400ConMensajeCoherente() throws Exception {
        when(usuarioAdminService.cambiarRol(21L, "NO_EXISTE"))
                .thenThrow(new IllegalArgumentException("El rol indicado no existe."));

        mockMvc.perform(patch("/api/usuarios/21/rol")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"rol\":\"NO_EXISTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El rol indicado no existe."));
    }

    @Test
    void usuarioInexistenteEnOperacionesDeEscrituraDevuelve404() throws Exception {
        RecursoNoEncontradoException notFound = new RecursoNoEncontradoException(
                "No existe un usuario con el ID 88.");
        when(usuarioAdminService.actualizar(eq(88L), any(ActualizarUsuarioRequest.class)))
                .thenThrow(notFound);
        when(usuarioAdminService.cambiarRol(88L, "NUTRICIONISTA")).thenThrow(notFound);
        when(usuarioAdminService.activar(88L)).thenThrow(notFound);
        when(usuarioAdminService.desactivar(88L)).thenThrow(notFound);
        doThrow(notFound).when(usuarioAdminService).cambiarContrasena(
                eq(88L), any(CambiarContrasenaUsuarioRequest.class));

        for (MockHttpServletRequestBuilder request : operacionesDeEscrituraParaIdInexistente()) {
            mockMvc.perform(request.with(user("admin").roles("ADMINISTRADOR")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
        }
    }

    @Test
    void actualizarUsuarioLlegaAlServicio() throws Exception {
        when(usuarioAdminService.actualizar(any(Long.class),
                any(ActualizarUsuarioRequest.class))).thenReturn(
                respuesta(21L, "Ana", "actualizada@example.com", "NUTRICIONISTA"));

        mockMvc.perform(put("/api/usuarios/21")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"actualizada@example.com"}
                                """))
                .andExpect(status().isOk());

        verify(usuarioAdminService).actualizar(any(Long.class),
                any(ActualizarUsuarioRequest.class));
    }

    @Test
    void cambioDeRolLlegaAlServicio() throws Exception {
        when(usuarioAdminService.cambiarRol(21L, "RECEPCIONISTA"))
                .thenReturn(respuesta(21L, "Ana", "ana@example.com", "RECEPCIONISTA"));

        mockMvc.perform(patch("/api/usuarios/21/rol")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"rol\":\"RECEPCIONISTA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"));

        verify(usuarioAdminService).cambiarRol(21L, "RECEPCIONISTA");
    }

    @Test
    void activacionYDesactivacionLlamanAlServicio() throws Exception {
        when(usuarioAdminService.activar(21L))
                .thenReturn(respuesta(21L, "Ana", "ana@example.com", "NUTRICIONISTA"));
        when(usuarioAdminService.desactivar(21L))
                .thenReturn(respuesta(21L, "Ana", "ana@example.com", "NUTRICIONISTA"));

        mockMvc.perform(patch("/api/usuarios/21/activar")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/usuarios/21/desactivar")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());

        verify(usuarioAdminService).activar(21L);
        verify(usuarioAdminService).desactivar(21L);
    }

    @Test
    void cambioDeContrasenaRespondeSinContenidoYDelegaAlServicio() throws Exception {
        doNothing().when(usuarioAdminService).cambiarContrasena(
                org.mockito.ArgumentMatchers.eq(21L),
                any(CambiarContrasenaUsuarioRequest.class));

        mockMvc.perform(patch("/api/usuarios/21/contrasena")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"contrasena\":\"NewStrongPassword!54\"}"))
                .andExpect(status().isNoContent());

        verify(usuarioAdminService).cambiarContrasena(
                org.mockito.ArgumentMatchers.eq(21L),
                any(CambiarContrasenaUsuarioRequest.class));
    }

    private UsuarioRespuesta respuesta(
            Long id, String nombres, String correo, String rol) {
        return new UsuarioRespuesta(id, nombres, "Prueba", correo, rol, "ACTIVO", null);
    }

    @Test
    void nutricionistaNoPuedeAdministrarUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .with(user("nutricionista").roles("NUTRICIONISTA")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void recepcionistaNoPuedeAdministrarUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios")
                        .with(user("recepcionista").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void entrenadorNoPuedeAdministrarUsuarios() throws Exception {
        for (MockHttpServletRequestBuilder request : todasLasRutasDeUsuarios()) {
            mockMvc.perform(request.with(user("entrenador").roles("ENTRENADOR")))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void entrenadorNoPuedeAccederAPagosMembresiasNiIngresos() throws Exception {
        var entrenador = user("entrenador").roles("ENTRENADOR");
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/pagos"),
                post("/api/pagos").contentType(APPLICATION_JSON).content("{}"),
                get("/api/membresias"),
                post("/api/membresias").contentType(APPLICATION_JSON).content("{}"),
                post("/api/ingresos").contentType(APPLICATION_JSON).content("{}"))) {
            mockMvc.perform(request.with(entrenador))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void apiSinReglaExplicitaNoSeAbreATodoUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/api/ruta-no-configurada")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void sinAutenticacionNoPuedeAdministrarUsuarios() throws Exception {
        mockMvc.perform(get("/api/usuarios"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void nutricionistaRecibe403EnTodasLasOperacionesDeUsuarios() throws Exception {
        for (MockHttpServletRequestBuilder request : todasLasRutasDeUsuarios()) {
            mockMvc.perform(request.with(user("nutricionista").roles("NUTRICIONISTA")))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void recepcionistaRecibe403EnTodasLasOperacionesDeUsuarios() throws Exception {
        for (MockHttpServletRequestBuilder request : todasLasRutasDeUsuarios()) {
            mockMvc.perform(request.with(user("recepcionista").roles("RECEPCIONISTA")))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(usuarioAdminService);
    }

    @Test
    void solicitudSinAutenticacionRecibe401EnTodasLasOperacionesDeUsuarios()
            throws Exception {
        for (MockHttpServletRequestBuilder request : todasLasRutasDeUsuarios()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }

        verifyNoInteractions(usuarioAdminService);
    }

    private List<MockHttpServletRequestBuilder> todasLasRutasDeUsuarios() {
        return List.of(
                get("/api/usuarios"),
                get("/api/usuarios/21"),
                post("/api/usuarios").contentType(APPLICATION_JSON).content("{}"),
                put("/api/usuarios/21").contentType(APPLICATION_JSON).content("{}"),
                patch("/api/usuarios/21/rol")
                        .contentType(APPLICATION_JSON).content("{}"),
                patch("/api/usuarios/21/activar"),
                patch("/api/usuarios/21/desactivar"),
                patch("/api/usuarios/21/contrasena")
                        .contentType(APPLICATION_JSON).content("{}"));
    }

    private List<MockHttpServletRequestBuilder> operacionesDeEscrituraParaIdInexistente() {
        return List.of(
                put("/api/usuarios/88").contentType(APPLICATION_JSON)
                        .content("""
                                {"nombres":"Ana","apellidos":"Prueba",
                                 "correo":"ana@example.com"}
                                """),
                patch("/api/usuarios/88/rol").contentType(APPLICATION_JSON)
                        .content("{\"rol\":\"NUTRICIONISTA\"}"),
                patch("/api/usuarios/88/activar"),
                patch("/api/usuarios/88/desactivar"),
                patch("/api/usuarios/88/contrasena").contentType(APPLICATION_JSON)
                        .content("{\"contrasena\":\"StrongTestPassword!42\"}"));
    }
}
