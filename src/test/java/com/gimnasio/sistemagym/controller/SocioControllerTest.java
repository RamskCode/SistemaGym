package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.dto.ActualizarSocioRequest;
import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.SocioService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(SocioController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class SocioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocioService socioService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void registrarRespondeCreatedConDto() throws Exception {
        when(socioService.registrar(any())).thenReturn(respuesta(12L, "SOC-0012"));

        mockMvc.perform(post("/api/socios")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"dni":"12345678","nombres":"Ana","apellidos":"Prueba"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idSocio").value(12))
                .andExpect(jsonPath("$.codigoSocio").value("SOC-0012"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
    }

    @Test
    void listarYBuscarDevuelvenDtosYConservanRutas() throws Exception {
        when(socioService.listarTodos()).thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.buscarPorCodigo("SOC-0012"))
                .thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.buscarPorDni("12345678"))
                .thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.buscarPorApellidos("Prue"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.buscarGlobal("Carlos Rojas"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));

        mockMvc.perform(get("/api/socios")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoSocio").value("SOC-0012"))
                .andExpect(jsonPath("$[0].class").doesNotExist());
        mockMvc.perform(get("/api/socios/codigo/SOC-0012")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/socios/dni/12345678")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/socios/buscar").param("apellidos", "Prue")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/socios/buscar").param("q", "Carlos Rojas")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoSocio").value("SOC-0012"));
        verify(socioService).buscarPorApellidos("Prue");
        verify(socioService).buscarGlobal("Carlos Rojas");
    }

    @Test
    void busquedaGlobalVaciaResponde400ConMensajeClaro() throws Exception {
        when(socioService.buscarGlobal("   ")).thenThrow(
                new IllegalArgumentException("El texto de búsqueda no puede estar vacío."));

        mockMvc.perform(get("/api/socios/buscar").param("q", "   ")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("El texto de búsqueda no puede estar vacío."));

        verify(socioService).buscarGlobal("   ");
    }

    @Test
    void estadoDevuelveDtosYEstadoInvalidoResponde400() throws Exception {
        when(socioService.listarPorEstado("ACTIVO"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.listarPorEstado("INACTIVO"))
                .thenReturn(List.of());
        when(socioService.listarPorEstado("PENDIENTE")).thenThrow(
                new IllegalArgumentException("El estado debe ser ACTIVO o INACTIVO."));

        mockMvc.perform(get("/api/socios/estado/ACTIVO")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ACTIVO"));
        mockMvc.perform(get("/api/socios/estado/INACTIVO")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/socios/estado/PENDIENTE")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("El estado debe ser ACTIVO o INACTIVO."));
    }

    @Test
    void consultarPorIdExistenteRespondeDtoSinCamposAdicionales() throws Exception {
        when(socioService.buscarPorId(12L)).thenReturn(respuesta(12L, "SOC-0012"));

        mockMvc.perform(get("/api/socios/12")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idSocio").value(12))
                .andExpect(jsonPath("$.fechaNacimiento").value("2000-01-02"))
                .andExpect(jsonPath("$.fechaRegistro").exists())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void consultaYActualizacionInexistentesResponden404() throws Exception {
        when(socioService.buscarPorId(404L)).thenThrow(
                new RecursoNoEncontradoException("No existe un socio con el ID 404."));
        when(socioService.actualizar(eq(404L), any(ActualizarSocioRequest.class)))
                .thenThrow(new RecursoNoEncontradoException(
                        "No existe un socio con el ID 404."));

        mockMvc.perform(get("/api/socios/404")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Recurso no encontrado"));
        mockMvc.perform(put("/api/socios/404")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombres\":\"Ana\",\"apellidos\":\"Prueba\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizaDatosYIgnoraCamposProtegidosDelJson() throws Exception {
        when(socioService.actualizar(eq(12L), any(ActualizarSocioRequest.class)))
                .thenReturn(respuesta(12L, "SOC-0012"));

        mockMvc.perform(put("/api/socios/12")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"dni":"12345678","nombres":"Ana","apellidos":"Prueba",
                                 "codigoSocio":"SOC-9999","estado":"INACTIVO",
                                 "idSocio":999,"fechaRegistro":"2020-01-01T00:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoSocio").value("SOC-0012"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        verify(socioService).actualizar(eq(12L), any(ActualizarSocioRequest.class));
    }

    @Test
    void dniDuplicadoResponde409() throws Exception {
        when(socioService.actualizar(eq(12L), any(ActualizarSocioRequest.class)))
                .thenThrow(new ConflictoException(
                        "El DNI ingresado ya pertenece a otro socio."));

        mockMvc.perform(put("/api/socios/12")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"dni\":\"12345678\",\"nombres\":\"Ana\",\"apellidos\":\"Prueba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje")
                        .value("El DNI ingresado ya pertenece a otro socio."));
    }

    @Test
    void dniDuplicadoAlRegistrarResponde409() throws Exception {
        when(socioService.registrar(any())).thenThrow(
                new ConflictoException("Ya existe un socio con ese DNI."));

        mockMvc.perform(post("/api/socios")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"dni\":\"12345678\",\"nombres\":\"Ana\",\"apellidos\":\"Prueba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void datosInvalidosResponden400AntesDeLlamarAlServicio() throws Exception {
        mockMvc.perform(put("/api/socios/12")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombres\":\" \",\"apellidos\":\"Prueba\",\"correo\":\"invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());

        verifyNoInteractions(socioService);
    }

    @Test
    void jsonMalformadoResponde400ConErrorSeguro() throws Exception {
        mockMvc.perform(post("/api/socios")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{no-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("El cuerpo de la solicitud no tiene un formato válido."));

        verifyNoInteractions(socioService);
    }

    @Test
    void accesoSinAutenticacionResponde401() throws Exception {
        mockMvc.perform(get("/api/socios"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/socios/buscar").param("q", "Carlos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/socios/estado/ACTIVO"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(socioService);
    }

    @Test
    void recepcionistaPuedeBuscarDocumentoInexistenteYObtieneListaVaciaSinAmpliarOtrosRoles()
            throws Exception {
        when(socioService.buscarGlobal("60770934")).thenReturn(List.of());
        mockMvc.perform(get("/api/socios/buscar").param("q", "60770934")
                        .with(user("recepcionista").roles("RECEPCIONISTA")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/socios/buscar").param("q", "60770934")
                        .with(user("coach").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/socios/buscar").param("q", "60770934")
                        .with(user("nutrition").roles("NUTRICIONISTA")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void nutricionistaRecibeDatosReducidosEnConsultasBusquedaYFiltro() throws Exception {
        when(socioService.listarTodos()).thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.buscarPorId(12L)).thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.buscarPorCodigo("SOC-0012"))
                .thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.buscarPorDni("12345678"))
                .thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.buscarGlobal("Ana"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.buscarPorApellidos("Prue"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));
        when(socioService.listarPorEstado("ACTIVO"))
                .thenReturn(List.of(respuesta(12L, "SOC-0012")));

        var nutricionista = user("nutricionista").roles("NUTRICIONISTA");
        mockMvc.perform(get("/api/socios").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idSocio").value(12))
                .andExpect(jsonPath("$[0].codigoSocio").exists())
                .andExpect(jsonPath("$[0].nombres").exists())
                .andExpect(jsonPath("$[0].apellidos").exists())
                .andExpect(jsonPath("$[0].estado").exists())
                .andExpect(jsonPath("$[0].dni").doesNotExist())
                .andExpect(jsonPath("$[0].fechaNacimiento").doesNotExist())
                .andExpect(jsonPath("$[0].celular").doesNotExist())
                .andExpect(jsonPath("$[0].correo").doesNotExist());
        mockMvc.perform(get("/api/socios/12").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idSocio").value(12))
                .andExpect(jsonPath("$.dni").doesNotExist());
        mockMvc.perform(get("/api/socios/codigo/SOC-0012").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idSocio").value(12))
                .andExpect(jsonPath("$.correo").doesNotExist());
        mockMvc.perform(get("/api/socios/dni/12345678").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idSocio").value(12))
                .andExpect(jsonPath("$.celular").doesNotExist());
        mockMvc.perform(get("/api/socios/buscar").param("q", "Ana").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idSocio").value(12))
                .andExpect(jsonPath("$[0].fechaNacimiento").doesNotExist());
        mockMvc.perform(get("/api/socios/buscar").param("apellidos", "Prue").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idSocio").value(12))
                .andExpect(jsonPath("$[0].dni").doesNotExist());
        mockMvc.perform(get("/api/socios/estado/ACTIVO").with(nutricionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].idSocio").value(12))
                .andExpect(jsonPath("$[0].correo").doesNotExist());
    }

    @Test
    void administradorYRecepcionistaPuedenActivarYDesactivar() throws Exception {
        when(socioService.activar(eq(12L), eq("Reactivación"), eq("admin@example.invalid")))
                .thenReturn(respuesta(12L, "SOC-0012"));
        when(socioService.desactivar(eq(12L), eq("Suspensión"), eq("recep@example.invalid")))
                .thenReturn(respuesta(12L, "SOC-0012"));

        mockMvc.perform(patch("/api/socios/12/activar")
                        .with(user("admin@example.invalid").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Reactivación\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/socios/12/desactivar")
                        .with(user("recep@example.invalid").roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Suspensión\"}"))
                .andExpect(status().isOk());

        verify(socioService).activar(12L, "Reactivación", "admin@example.invalid");
        verify(socioService).desactivar(12L, "Suspensión", "recep@example.invalid");
    }

    @Test
    void nutricionistaNoPuedeRegistrarActualizarActivarNiDesactivar() throws Exception {
        var nutricionista = user("nutricionista").roles("NUTRICIONISTA");
        mockMvc.perform(post("/api/socios").with(nutricionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombres\":\"Ana\",\"apellidos\":\"Prueba\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/socios/12").with(nutricionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"nombres\":\"Ana\",\"apellidos\":\"Prueba\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/socios/12/activar").with(nutricionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Motivo\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/socios/12/desactivar").with(nutricionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Motivo\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(socioService);
    }

    @Test
    void motivoEsObligatorioYNoAceptaResponsableEnElCuerpo() throws Exception {
        var recepcionista = user("recep@example.invalid").roles("RECEPCIONISTA");
        mockMvc.perform(patch("/api/socios/12/activar").with(recepcionista)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/socios/12/desactivar").with(recepcionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/socios/12/activar").with(recepcionista)
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Motivo\",\"id_usuario\":999}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(socioService);
    }

    private SocioRespuesta respuesta(Long id, String codigo) {
        return new SocioRespuesta(id, codigo, "12345678", "DNI", "12345678", "Ana", "Prueba",
                LocalDate.of(2000, 1, 2), "999999999", "ana@example.com", "ACTIVO",
                LocalDateTime.of(2024, 1, 2, 3, 4));
    }
}
