package com.gimnasio.sistemagym.controller;

import com.gimnasio.sistemagym.config.JwtAuthenticationEntryPoint;
import com.gimnasio.sistemagym.config.JwtAuthenticationFilter;
import com.gimnasio.sistemagym.config.JwtProperties;
import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.config.SecurityConfig;
import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralRespuesta;
import com.gimnasio.sistemagym.dto.AsistenciaLaboralHistorialRespuesta;
import com.gimnasio.sistemagym.dto.ReporteAsistenciaLaboralEmpleadoRespuesta;
import com.gimnasio.sistemagym.dto.PersonalSeleccionableRespuesta;
import com.gimnasio.sistemagym.dto.EmpleadoConHistorialRespuesta;
import com.gimnasio.sistemagym.entity.TipoEventoAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.service.AsistenciaLaboralService;
import com.gimnasio.sistemagym.service.CustomUserDetailsService;
import com.gimnasio.sistemagym.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AsistenciaLaboralController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtProperties.class,
        JwtTokenService.class, JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class})
class AsistenciaLaboralControllerSecurityTest {

    private static final String CORREO_RECEPCIONISTA = "recepcionista@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @MockitoBean
    private AsistenciaLaboralService asistenciaLaboralService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void marcacionPropiaObtieneIdentidadAutenticadaYNoAceptaCuerpo() throws Exception {
        AsistenciaLaboral entrada = asistencia(85L, null);
        AsistenciaLaboral salida = asistencia(85L, LocalDateTime.parse("2026-11-06T05:30:00"));
        when(asistenciaLaboralService.registrarMiEntradaAutenticada(CORREO_RECEPCIONISTA))
                .thenReturn(entrada);
        when(asistenciaLaboralService.registrarMiSalidaAutenticada(CORREO_RECEPCIONISTA))
                .thenReturn(salida);

        mockMvc.perform(post("/api/asistencias-laborales/mi-entrada")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idEmpleado").value(25));
        mockMvc.perform(post("/api/asistencias-laborales/mi-salida")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEmpleado").value(25));

        for (String ruta : new String[] {"mi-entrada", "mi-salida"}) {
            mockMvc.perform(post("/api/asistencias-laborales/" + ruta)
                            .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                            .contentType(APPLICATION_JSON)
                            .content("{\"idEmpleado\":999}"))
                    .andExpect(status().isBadRequest());
        }
        for (String rol : new String[] {"ADMINISTRADOR", "ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(post("/api/asistencias-laborales/mi-entrada")
                            .with(user("usuario@test").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/asistencias-laborales/mi-entrada"))
                .andExpect(status().isUnauthorized());

        verify(asistenciaLaboralService).registrarMiEntradaAutenticada(CORREO_RECEPCIONISTA);
        verify(asistenciaLaboralService).registrarMiSalidaAutenticada(CORREO_RECEPCIONISTA);
        verifyNoMoreInteractions(asistenciaLaboralService);
    }

    @Test
    void personalSeleccionableSoloParaRecepcionYAdminYExponeDtoMinimo() throws Exception {
        when(asistenciaLaboralService.listarPersonalSeleccionable()).thenReturn(List.of(
                new PersonalSeleccionableRespuesta(
                        25L, "Luis", "Entrenador", "ENTRENADOR")));

        for (String rol : new String[] {"ADMINISTRADOR", "RECEPCIONISTA"}) {
            mockMvc.perform(get("/api/asistencias-laborales/personal-seleccionable")
                            .with(user("usuario@test").roles(rol)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].idEmpleado").value(25))
                    .andExpect(jsonPath("$[0].nombres").value("Luis"))
                    .andExpect(jsonPath("$[0].apellidos").value("Entrenador"))
                    .andExpect(jsonPath("$[0].rol").value("ENTRENADOR"))
                    .andExpect(jsonPath("$[0].correo").doesNotExist())
                    .andExpect(jsonPath("$[0].numeroDocumento").doesNotExist())
                    .andExpect(jsonPath("$[0].contrasenaHash").doesNotExist());
        }

        mockMvc.perform(get("/api/asistencias-laborales/personal-seleccionable"))
                .andExpect(status().isUnauthorized());
        for (String rol : new String[] {"ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(get("/api/asistencias-laborales/personal-seleccionable")
                            .with(user("usuario@test").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        verify(asistenciaLaboralService, org.mockito.Mockito.times(2))
                .listarPersonalSeleccionable();
    }

    @Test
    void empleadosConHistorialEsConsultaProtegidaParaAdministradorYRecepcionista() throws Exception {
        when(asistenciaLaboralService.listarEmpleadosConHistorial("admin@test"))
                .thenReturn(List.of(new EmpleadoConHistorialRespuesta(
                        44L, "Maricarmen", "Santos", "RECEPCIONISTA")));

        mockMvc.perform(get("/api/asistencias-laborales/empleados-con-historial")
                        .with(user("admin@test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEmpleado").value(44))
                .andExpect(jsonPath("$[0].nombres").value("Maricarmen"))
                .andExpect(jsonPath("$[0].rol").value("RECEPCIONISTA"));

        mockMvc.perform(get("/api/asistencias-laborales/empleados-con-historial")
                        .with(user("recepcion@test").roles("RECEPCIONISTA")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/asistencias-laborales/empleados-con-historial"))
                .andExpect(status().isUnauthorized());

        verify(asistenciaLaboralService).listarEmpleadosConHistorial("admin@test");
        verify(asistenciaLaboralService).listarEmpleadosConHistorial("recepcion@test");
        verifyNoMoreInteractions(asistenciaLaboralService);
    }

    @Test
    void historialRecepcionistaEsLecturaProtegidaYAdmiteFiltrosYPaginacion() throws Exception {
        var attendance = new AsistenciaLaboralRespuesta(18L, 44L, "Maricarmen", "Santos",
                "RECEPCIONISTA", Instant.parse("2026-11-12T14:00:00Z"), null,
                LocalDate.parse("2026-11-12"), EstadoAsistenciaLaboral.ACTIVA);
        when(asistenciaLaboralService.consultarHistorialRecepcionista(
                "recepcion@test", 44L, 1, 10, LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-12"), EstadoAsistenciaLaboral.ACTIVA))
                .thenReturn(new PageImpl<>(List.of(attendance), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/asistencias-laborales/consulta-recepcionista")
                        .with(user("recepcion@test").roles("RECEPCIONISTA"))
                        .param("page", "1").param("size", "10")
                        .param("fechaDesde", "2026-11-10").param("fechaHasta", "2026-11-12")
                        .param("estado", "ACTIVA").param("idEmpleado", "44"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].nombresEmpleado").value("Maricarmen"))
                .andExpect(jsonPath("$.contenido[0].rolEmpleado").value("RECEPCIONISTA"))
                .andExpect(jsonPath("$.contenido[0].fechaSalidaUtc").doesNotExist())
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalElementos").value(11));

        mockMvc.perform(get("/api/asistencias-laborales/consulta-recepcionista")
                        .with(user("admin@test").roles("ADMINISTRADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/asistencias-laborales/consulta-recepcionista"))
                .andExpect(status().isUnauthorized());
        verify(asistenciaLaboralService).consultarHistorialRecepcionista(
                "recepcion@test", 44L, 1, 10, LocalDate.parse("2026-11-10"),
                LocalDate.parse("2026-11-12"), EstadoAsistenciaLaboral.ACTIVA);
        verifyNoMoreInteractions(asistenciaLaboralService);
    }

    @Test
    void entradaDevuelve201YUsaSubjectJwtComoRegistrador() throws Exception {
        var principal = User.withUsername(CORREO_RECEPCIONISTA)
                .password("unused").roles("RECEPCIONISTA").build();
        when(userDetailsService.loadUserByUsername(CORREO_RECEPCIONISTA))
                .thenReturn(principal);
        AsistenciaLaboral entrada = asistencia(81L, null);
        when(asistenciaLaboralService.registrarEntradaAutenticada(
                25L, CORREO_RECEPCIONISTA)).thenReturn(entrada);

        mockMvc.perform(post("/api/asistencias-laborales/entrada")
                        .header("Authorization", "Bearer " + tokenService.generar(principal))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idEmpleado\":25}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAsistencia").value(81))
                .andExpect(jsonPath("$.idEmpleado").value(25))
                .andExpect(jsonPath("$.fechaEntradaUtc").value(
                        "2026-11-06T04:59:00Z"))
                .andExpect(jsonPath("$.fechaSalidaUtc").doesNotExist())
                .andExpect(jsonPath("$.fechaLaboral").value("2026-11-05"));

        verify(asistenciaLaboralService).registrarEntradaAutenticada(
                25L, CORREO_RECEPCIONISTA);
    }

    @Test
    void salidaDevuelve200YUsaAuthenticationName() throws Exception {
        AsistenciaLaboral salida = asistencia(81L,
                LocalDateTime.parse("2026-11-06T05:30:00"));
        when(asistenciaLaboralService.registrarSalidaAutenticada(
                25L, CORREO_RECEPCIONISTA)).thenReturn(salida);

        mockMvc.perform(post("/api/asistencias-laborales/salida")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idEmpleado\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaSalidaUtc").value(
                        "2026-11-06T05:30:00Z"));

        verify(asistenciaLaboralService).registrarSalidaAutenticada(
                25L, CORREO_RECEPCIONISTA);
    }

    @Test
    void correccionAdminUsaElSubjectAutenticadoYDevuelve200() throws Exception {
        AsistenciaLaboral corregida = asistencia(81L, null);
        when(asistenciaLaboralService.corregirAsistencia(
                eq(81L), eq(Instant.parse("2026-11-06T04:50:00Z")),
                org.mockito.ArgumentMatchers.isNull(), eq("Ajuste autorizado"), eq("admin@test")))
                .thenReturn(corregida);

        mockMvc.perform(patch("/api/asistencias-laborales/81/correccion")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\","
                                + "\"motivo\":\"Ajuste autorizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAsistencia").value(81))
                .andExpect(jsonPath("$.idEmpleado").value(25));

        verify(asistenciaLaboralService).corregirAsistencia(
                81L, Instant.parse("2026-11-06T04:50:00Z"), null,
                "Ajuste autorizado", "admin@test");
    }

    @Test
    void administradorYRecepcionistaPuedenSolicitarAnulacionYUsaElSubjectAutenticado() throws Exception {
        AsistenciaLaboral anulada = asistencia(81L, null);
        when(asistenciaLaboralService.anularAsistencia(81L, "Duplicado", "admin@test"))
                .thenReturn(anulada);
        when(asistenciaLaboralService.anularAsistencia(81L, "Duplicado", CORREO_RECEPCIONISTA))
                .thenReturn(anulada);
        mockMvc.perform(patch("/api/asistencias-laborales/81/anulacion")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Duplicado\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/asistencias-laborales/81/anulacion")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"Duplicado\"}"))
                .andExpect(status().isOk());

        verify(asistenciaLaboralService).anularAsistencia(81L, "Duplicado", "admin@test");
        verify(asistenciaLaboralService).anularAsistencia(81L, "Duplicado", CORREO_RECEPCIONISTA);
        org.mockito.Mockito.verifyNoMoreInteractions(asistenciaLaboralService);
    }

    @Test
    void rechazaRolesNoAutorizadosEnCorreccionYAnulacion() throws Exception {
        for (String rol : new String[] {"RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(patch("/api/asistencias-laborales/81/correccion")
                            .with(user("usuario@test").roles(rol))
                            .contentType(APPLICATION_JSON)
                            .content("{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\","
                                    + "\"motivo\":\"Corrección\"}"))
                    .andExpect(status().isForbidden());
        }
        for (String rol : new String[] {"ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(patch("/api/asistencias-laborales/81/anulacion")
                            .with(user("usuario@test").roles(rol))
                            .contentType(APPLICATION_JSON)
                            .content("{\"motivo\":\"Anulación\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void rechazaSolicitudesInvalidasYNoAceptaActorEnElCuerpo() throws Exception {
        var admin = user("admin@test").roles("ADMINISTRADOR");
        for (String cuerpo : new String[] {
                "{}",
                "{\"fechaEntradaUtc\":\"no-es-instante\",\"motivo\":\"Cambio\"}",
                "{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\"}",
                "{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\",\"motivo\":\" \"}",
                "{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\","
                        + "\"motivo\":\"" + "x".repeat(501) + "\"}",
                "{\"fechaEntradaUtc\":\"2026-11-06T04:50:00Z\","
                        + "\"motivo\":\"Cambio\",\"actor\":\"recepcionista@test\"}"
        }) {
            mockMvc.perform(patch("/api/asistencias-laborales/81/correccion")
                            .with(admin).contentType(APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }

        var adminAnulacion = user("admin@test").roles("ADMINISTRADOR");
        for (String cuerpo : new String[] {
                "{}", "{\"motivo\":\"  \"}",
                "{\"motivo\":\"" + "x".repeat(501) + "\"}",
                "{\"motivo\":\"Duplicado\",\"actorId\":25}"
        }) {
            mockMvc.perform(patch("/api/asistencias-laborales/81/anulacion")
                            .with(adminAnulacion).contentType(APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void propagaElRechazoDeNegocioDelServicioAlEndpointDeAnulacion() throws Exception {
        when(asistenciaLaboralService.anularAsistencia(
                81L, "No corresponde", "admin@test"))
                .thenThrow(new AccessDeniedException("No se permite anular esta asistencia."));

        mockMvc.perform(patch("/api/asistencias-laborales/81/anulacion")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"motivo\":\"No corresponde\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloRecepcionistaAutenticadaPuedeInvocarLosEndpoints() throws Exception {
        mockMvc.perform(post("/api/asistencias-laborales/entrada")
                        .contentType(APPLICATION_JSON).content("{\"idEmpleado\":25}"))
                .andExpect(status().isUnauthorized());

        for (String rol : new String[] {
                "ADMINISTRADOR", "ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(post("/api/asistencias-laborales/salida")
                            .with(user("usuario@example.test").roles(rol))
                            .contentType(APPLICATION_JSON)
                            .content("{\"idEmpleado\":25}"))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void rechazaIdsAusentesNoPositivosYJsonAdicional() throws Exception {
        var recepcionista = user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA");
        for (String body : new String[] {
                "{}",
                "{\"idEmpleado\":0}",
                "{\"idEmpleado\":-4}",
                "{\"idEmpleado\":25,\"idRegistrador\":7}",
                "{\"idEmpleado\":25,\"fechaEntradaUtc\":\"2026-11-06T04:00:00Z\"}",
                "{\"idEmpleado\":25,\"hora\":\"10:00\"}"
        }) {
            mockMvc.perform(post("/api/asistencias-laborales/entrada")
                            .with(recepcionista)
                            .contentType(APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void propaga404ParaEmpleadoInexistenteY409ParaConflicto() throws Exception {
        when(asistenciaLaboralService.registrarEntradaAutenticada(
                999L, CORREO_RECEPCIONISTA))
                .thenThrow(new RecursoNoEncontradoException("No existe empleado."));
        when(asistenciaLaboralService.registrarSalidaAutenticada(
                25L, CORREO_RECEPCIONISTA))
                .thenThrow(new ConflictoException("No hay entrada abierta."));

        mockMvc.perform(post("/api/asistencias-laborales/entrada")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idEmpleado\":999}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/asistencias-laborales/salida")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idEmpleado\":25}"))
                .andExpect(status().isConflict());
    }

    @Test
    void recepcionistaRecibe403SiElServicioRechazaAlEmpleadoObjetivo() throws Exception {
        when(asistenciaLaboralService.registrarEntradaAutenticada(
                999L, CORREO_RECEPCIONISTA))
                .thenThrow(new AccessDeniedException("Objetivo no permitido."));

        mockMvc.perform(post("/api/asistencias-laborales/entrada")
                        .with(user(CORREO_RECEPCIONISTA).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idEmpleado\":999}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultaPropiaResuelveUsuarioDesdeSubjectJwtYUsaPaginacionPredeterminada()
            throws Exception {
        var principal = User.withUsername(CORREO_RECEPCIONISTA)
                .password("unused").roles("RECEPCIONISTA").build();
        when(userDetailsService.loadUserByUsername(CORREO_RECEPCIONISTA))
                .thenReturn(principal);
        when(asistenciaLaboralService.consultarMias(
                CORREO_RECEPCIONISTA, 0, 20, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias")
                        .header("Authorization", "Bearer " + tokenService.generar(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isArray())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));

        verify(asistenciaLaboralService).consultarMias(
                CORREO_RECEPCIONISTA, 0, 20, null, null);
    }

    @Test
    void rolesYPermisosDeConsultasYValidacionDePaginacion() throws Exception {
        when(asistenciaLaboralService.consultarMias("recepcionista", -1, 20, null, null))
                .thenThrow(new IllegalArgumentException("page inválido"));
        when(asistenciaLaboralService.consultarMias("recepcionista", 0, 30, null, null))
                .thenThrow(new IllegalArgumentException("size inválido"));
        when(asistenciaLaboralService.consultarMias("recepcionista", 0, 20,
                LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 1)))
                .thenThrow(new IllegalArgumentException("rango inválido"));
        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/asistencias-laborales")
                        .with(user("entrenador").roles("ENTRENADOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/asistencias-laborales/empleado/25")
                        .with(user("recepcionista").roles("RECEPCIONISTA")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .param("size", "30"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .param("fechaDesde", "2026-12-02")
                        .param("fechaHasta", "2026-12-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/mis-asistencias")
                        .with(user("recepcionista").roles("RECEPCIONISTA"))
                        .param("fechaDesde", "no-es-fecha"))
                .andExpect(status().isBadRequest());

        verify(asistenciaLaboralService, never()).consultarTodas(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.nullable(LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(EstadoAsistenciaLaboral.class));
        verify(asistenciaLaboralService, never()).consultarEmpleado(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.nullable(LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(EstadoAsistenciaLaboral.class));
    }

    @Test
    void aceptaTamanoPermitidoYSerializaContenidoEnUtcSinExponerJpa() throws Exception {
        var respuesta = new AsistenciaLaboralRespuesta(8L, 25L, "Ana", "Díaz", "ENTRENADOR",
                Instant.parse("2026-11-06T04:59:00Z"), null,
                LocalDate.of(2026, 11, 5), EstadoAsistenciaLaboral.ACTIVA);
        when(asistenciaLaboralService.consultarTodas(eq("admin"), eq(0), eq(50),
                eq(LocalDate.of(2026, 11, 5)), eq(LocalDate.of(2026, 11, 5)),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new PageImpl<>(List.of(respuesta), PageRequest.of(0, 50), 1));

        mockMvc.perform(get("/api/asistencias-laborales")
                        .with(user("admin").roles("ADMINISTRADOR"))
                        .param("size", "50")
                        .param("fechaDesde", "2026-11-05")
                        .param("fechaHasta", "2026-11-05")
                        .param("sort", "idEmpleado,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idAsistencia").value(8))
                .andExpect(jsonPath("$.contenido[0].fechaEntradaUtc")
                        .value("2026-11-06T04:59:00Z"))
                .andExpect(jsonPath("$.contenido[0].nombres").doesNotExist())
                .andExpect(jsonPath("$.tamano").value(50));
    }

    @Test
    void empleadoAdminPuedeConsultarYEmpleadoInexistenteDevuelve404() throws Exception {
        when(asistenciaLaboralService.consultarEmpleado(25L, "admin", 0, 20,
                null, null, null))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(asistenciaLaboralService.consultarEmpleado(999L, "admin", 0, 20,
                null, null, null))
                .thenThrow(new RecursoNoEncontradoException("No existe empleado."));

        mockMvc.perform(get("/api/asistencias-laborales/empleado/25")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido").isEmpty());
        mockMvc.perform(get("/api/asistencias-laborales/empleado/999")
                        .with(user("admin").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialAdminDevuelveDetalleEventosAuditoriasYUsaIdentidadAutenticada()
            throws Exception {
        var evento = new AsistenciaLaboralHistorialRespuesta.Evento(
                901L, TipoEventoAsistenciaLaboral.ENTRADA,
                Instant.parse("2026-11-06T04:00:00Z"),
                new AsistenciaLaboralHistorialRespuesta.Persona(
                        31L, "Rosa", "Recepción", "recepcionista@test"));
        var auditoria = new AsistenciaLaboralHistorialRespuesta.Auditoria(
                701L, TipoOperacionAuditoriaAsistenciaLaboral.CORRECCION,
                "Ajuste autorizado", Instant.parse("2026-11-06T06:00:00Z"),
                new AsistenciaLaboralHistorialRespuesta.Persona(
                        11L, "Ada", "Admin", "admin@test"),
                EstadoAsistenciaLaboral.ACTIVA, EstadoAsistenciaLaboral.ACTIVA,
                Instant.parse("2026-11-06T04:00:00Z"), null,
                Instant.parse("2026-11-06T03:59:00Z"), null);
        var historial = new AsistenciaLaboralHistorialRespuesta(
                81L,
                new AsistenciaLaboralHistorialRespuesta.Persona(
                        25L, "Luis", "Entrenador", "entrenador@test"),
                Instant.parse("2026-11-06T03:59:00Z"), null,
                LocalDate.of(2026, 11, 5), EstadoAsistenciaLaboral.ACTIVA,
                new AsistenciaLaboralHistorialRespuesta.Persona(
                        31L, "Rosa", "Recepción", "recepcionista@test"),
                null, List.of(evento), List.of(auditoria));
        when(asistenciaLaboralService.consultarHistorialAsistencia(81L, "admin@test"))
                .thenReturn(historial);

        mockMvc.perform(get("/api/asistencias-laborales/81/historial")
                        .with(user("admin@test").roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAsistencia").value(81))
                .andExpect(jsonPath("$.empleado.idUsuario").value(25))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.fechaEntradaUtc").value("2026-11-06T03:59:00Z"))
                .andExpect(jsonPath("$.registradorEntrada.correo")
                        .value("recepcionista@test"))
                .andExpect(jsonPath("$.eventos[0].tipoEvento").value("ENTRADA"))
                .andExpect(jsonPath("$.eventos[0].fechaHoraUtc")
                        .value("2026-11-06T04:00:00Z"))
                .andExpect(jsonPath("$.auditorias[0].tipoOperacion").value("CORRECCION"))
                .andExpect(jsonPath("$.auditorias[0].actor.correo").value("admin@test"))
                .andExpect(jsonPath("$.auditorias[0].motivo").value("Ajuste autorizado"))
                .andExpect(jsonPath("$.auditorias[0].fechaEntradaNuevaUtc")
                        .value("2026-11-06T03:59:00Z"))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.eventos[0].registrador.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.auditorias[0].actor.contrasenaHash").doesNotExist());

        verify(asistenciaLaboralService).consultarHistorialAsistencia(81L, "admin@test");
    }

    @Test
    void historialNiegaAccesoSinAutenticacionYAUsuariosNoAdministradores()
            throws Exception {
        mockMvc.perform(get("/api/asistencias-laborales/81/historial"))
                .andExpect(status().isUnauthorized());

        for (String rol : new String[] {"RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(get("/api/asistencias-laborales/81/historial")
                            .with(user("usuario@test").roles(rol)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void historialDeAsistenciaInexistenteDevuelve404() throws Exception {
        when(asistenciaLaboralService.consultarHistorialAsistencia(999L, "admin@test"))
                .thenThrow(new RecursoNoEncontradoException("No existe asistencia."));

        mockMvc.perform(get("/api/asistencias-laborales/999/historial")
                        .with(user("admin@test").roles("ADMINISTRADOR")))
                .andExpect(status().isNotFound());

        verify(asistenciaLaboralService).consultarHistorialAsistencia(999L, "admin@test");
    }

    @Test
    void reporteAdminDevuelveContadoresHorasYUsaIdentidadAutenticada() throws Exception {
        var reporte = new ReporteAsistenciaLaboralEmpleadoRespuesta(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                3, 1, 2, 1, new BigDecimal("12.75"));
        when(asistenciaLaboralService.consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test"))
                .thenReturn(reporte);

        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idEmpleado").value(25))
                .andExpect(jsonPath("$.fechaDesde").value("2026-11-01"))
                .andExpect(jsonPath("$.fechaHasta").value("2026-11-30"))
                .andExpect(jsonPath("$.cantidadActivas").value(3))
                .andExpect(jsonPath("$.cantidadAnuladas").value(1))
                .andExpect(jsonPath("$.jornadasCerradas").value(2))
                .andExpect(jsonPath("$.jornadasAbiertas").value(1))
                .andExpect(jsonPath("$.totalHorasRegistradas").value(12.75))
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(asistenciaLaboralService).consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test");
    }

    @Test
    void reporteSinAsistenciasDevuelveRespuestaVaciaConCeros() throws Exception {
        when(asistenciaLaboralService.consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test"))
                .thenReturn(new ReporteAsistenciaLaboralEmpleadoRespuesta(
                        25L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                        0, 0, 0, 0, new BigDecimal("0.00")));

        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadActivas").value(0))
                .andExpect(jsonPath("$.cantidadAnuladas").value(0))
                .andExpect(jsonPath("$.jornadasCerradas").value(0))
                .andExpect(jsonPath("$.jornadasAbiertas").value(0))
                .andExpect(jsonPath("$.totalHorasRegistradas").value(0.00));
    }

    @Test
    void reporteRestringeAccesoAAdministrador() throws Exception {
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isUnauthorized());

        for (String rol : new String[] {"RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA"}) {
            mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                            .with(user("usuario@test").roles(rol))
                            .param("fechaDesde", "2026-11-01")
                            .param("fechaHasta", "2026-11-30"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(asistenciaLaboralService);
    }

    @Test
    void reporteDevuelve404ParaEmpleadoInexistente() throws Exception {
        when(asistenciaLaboralService.consultarReporteEmpleado(
                999L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test"))
                .thenThrow(new RecursoNoEncontradoException("No existe empleado."));

        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/999")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reporteRechazaIdFechasObligatoriasYFechasConFormatoInvalido() throws Exception {
        var admin = user("admin@test").roles("ADMINISTRADOR");
        when(asistenciaLaboralService.consultarReporteEmpleado(
                0L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test"))
                .thenThrow(new IllegalArgumentException("El ID debe ser positivo."));
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/abc")
                        .with(admin)
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/0")
                        .with(admin)
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(admin).param("fechaHasta", "2026-11-30"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(admin).param("fechaDesde", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(admin)
                        .param("fechaDesde", "01-11-2026")
                        .param("fechaHasta", "2026-11-30"))
                .andExpect(status().isBadRequest());
        verify(asistenciaLaboralService).consultarReporteEmpleado(
                0L, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "admin@test");
        verifyNoMoreInteractions(asistenciaLaboralService);
    }

    @Test
    void reporteDevuelve400ParaRangoInvertidoYPeriodoMayorA12Meses() throws Exception {
        when(asistenciaLaboralService.consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), "admin@test"))
                .thenThrow(new IllegalArgumentException("Rango invertido."));
        when(asistenciaLaboralService.consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 2), "admin@test"))
                .thenThrow(new IllegalArgumentException("Período demasiado largo."));

        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("fechaDesde", "2026-11-30")
                        .param("fechaHasta", "2026-11-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/asistencias-laborales/reportes/empleados/25")
                        .with(user("admin@test").roles("ADMINISTRADOR"))
                        .param("fechaDesde", "2026-11-01")
                        .param("fechaHasta", "2027-11-02"))
                .andExpect(status().isBadRequest());

        verify(asistenciaLaboralService).consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 30), LocalDate.of(2026, 11, 1), "admin@test");
        verify(asistenciaLaboralService).consultarReporteEmpleado(
                25L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 11, 2), "admin@test");
    }

    private AsistenciaLaboral asistencia(Long id, LocalDateTime salidaUtc) {
        AsistenciaLaboral asistencia = mock(AsistenciaLaboral.class);
        Usuario usuario = mock(Usuario.class);
        when(usuario.getIdUsuario()).thenReturn(25L);
        when(asistencia.getUsuario()).thenReturn(usuario);
        when(asistencia.getIdAsistencia()).thenReturn(id);
        when(asistencia.getFechaEntradaUtc())
                .thenReturn(LocalDateTime.parse("2026-11-06T04:59:00"));
        when(asistencia.getFechaSalidaUtc()).thenReturn(salidaUtc);
        when(asistencia.getFechaLaboral()).thenReturn(LocalDate.of(2026, 11, 5));
        when(asistencia.getEstado()).thenReturn(EstadoAsistenciaLaboral.ACTIVA);
        return asistencia;
    }
}
