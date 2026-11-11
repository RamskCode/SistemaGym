package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.entity.Auditoria;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.AuditoriaRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.service.SocioService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(OrderAnnotation.class)
class SocioEstadoAuditoriaMySql84IT extends MySql84IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SocioService socioService;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private AuditoriaRepository auditoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private AuditoriaRepository auditoriaRepositorySpy;

    private final List<Long> sociosCreados = new ArrayList<>();
    private Usuario actor;
    private Rol rolActor;

    @org.junit.jupiter.api.BeforeEach
    void crearActorDePrueba() {
        rolActor = new Rol();
        rolActor.setNombre("ADMINISTRADOR");
        rolActor = rolRepository.saveAndFlush(rolActor);

        actor = new Usuario();
        actor.setRol(rolActor);
        actor.setNombres("Actor Integracion");
        actor.setApellidos("Estado Socio");
        actor.setCorreo("actor-" + UUID.randomUUID() + "@example.test");
        actor.setContrasenaHash("hash-fixture-no-utilizado-para-autenticacion");
        actor.setEstado("ACTIVO");
        actor = usuarioRepository.saveAndFlush(actor);
    }

    @AfterEach
    void limpiarSoloFixturesDeEstaPrueba() {
        for (Long idSocio : sociosCreados) {
            jdbcTemplate.update(
                    "DELETE FROM auditoria WHERE entidad = 'SOCIO' AND id_registro = ?",
                    idSocio.toString());
            socioRepository.deleteById(idSocio);
        }
        sociosCreados.clear();

        if (actor != null) {
            jdbcTemplate.update("DELETE FROM auditoria WHERE id_usuario = ?",
                    actor.getIdUsuario());
            usuarioRepository.deleteById(actor.getIdUsuario());
        }
        if (rolActor != null) {
            rolRepository.deleteById(rolActor.getIdRol());
        }
    }

    @Test
    @Order(1)
    void desactivacionActualizaEstadoYRegistraAuditoriaDelActorAutenticado()
            throws Exception {
        SocioRespuesta socio = registrarSocio();
        String motivo = "Solicitud de pausa del socio";

        mockMvc.perform(patch("/api/socios/{id}/desactivar", socio.idSocio())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoMotivo(motivo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));

        assertEquals("INACTIVO", socioRepository.findById(socio.idSocio())
                .orElseThrow().getEstado());
        List<Auditoria> auditorias = auditoriasDelSocio(socio.idSocio());
        assertEquals(1, auditorias.size());
        verificarAuditoria(auditorias.getFirst(), socio.idSocio(),
                "DESACTIVAR_SOCIO", motivo, "ACTIVO", "INACTIVO");
    }

    @Test
    @Order(2)
    void activacionRegistraLaTransicionInversa() throws Exception {
        SocioRespuesta socio = registrarSocio();
        String motivoDesactivacion = "Pausa solicitada";
        String motivoActivacion = "Reactivacion autorizada";

        cambiarEstado(socio.idSocio(), "desactivar", motivoDesactivacion)
                .andExpect(status().isOk());
        cambiarEstado(socio.idSocio(), "activar", motivoActivacion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));

        assertEquals("ACTIVO", socioRepository.findById(socio.idSocio())
                .orElseThrow().getEstado());
        List<Auditoria> auditorias = auditoriasDelSocio(socio.idSocio());
        assertEquals(2, auditorias.size());
        assertAuditoriaPresente(auditorias, socio.idSocio(), "DESACTIVAR_SOCIO",
                motivoDesactivacion, "ACTIVO", "INACTIVO");
        assertAuditoriaPresente(auditorias, socio.idSocio(), "ACTIVAR_SOCIO",
                motivoActivacion, "INACTIVO", "ACTIVO");
    }

    @Test
    @Order(3)
    void lasTransicionesRedundantesDevuelven409SinAuditoriaAdicional()
            throws Exception {
        SocioRespuesta socio = registrarSocio();

        cambiarEstado(socio.idSocio(), "desactivar", "Primera desactivacion")
                .andExpect(status().isOk());
        assertEquals(1, auditoriasDelSocio(socio.idSocio()).size());

        cambiarEstado(socio.idSocio(), "desactivar", "Repetir desactivacion")
                .andExpect(status().isConflict());
        assertEquals("INACTIVO", socioRepository.findById(socio.idSocio())
                .orElseThrow().getEstado());
        assertEquals(1, auditoriasDelSocio(socio.idSocio()).size());

        cambiarEstado(socio.idSocio(), "activar", "Activacion valida")
                .andExpect(status().isOk());
        assertEquals(2, auditoriasDelSocio(socio.idSocio()).size());

        cambiarEstado(socio.idSocio(), "activar", "Repetir activacion")
                .andExpect(status().isConflict());
        assertEquals("ACTIVO", socioRepository.findById(socio.idSocio())
                .orElseThrow().getEstado());
        assertEquals(2, auditoriasDelSocio(socio.idSocio()).size());
    }

    @Test
    @Order(4)
    void unFalloAlGuardarAuditoriaRevierteElCambioDeEstado() throws Exception {
        SocioRespuesta socio = registrarSocio();
        doThrow(new DataIntegrityViolationException(
                "Fallo de auditoria inyectado por la prueba"))
                .when(auditoriaRepositorySpy).saveAndFlush(any(Auditoria.class));

        cambiarEstado(socio.idSocio(), "desactivar", "Fallo controlado")
                .andExpect(status().isInternalServerError());

        assertEquals("ACTIVO", socioRepository.findById(socio.idSocio())
                .orElseThrow().getEstado());
        assertTrue(auditoriasDelSocio(socio.idSocio()).isEmpty(),
                "No debe quedar auditoria si la transaccion revierte el estado.");
    }

    private SocioRespuesta registrarSocio() {
        String token = UUID.randomUUID().toString().replace("-", "");
        Socio socio = new Socio();
        String dni = String.format(java.util.Locale.ROOT, "%08d",
                Math.floorMod(token.hashCode(), 100_000_000));
        socio.setDni(dni);
        socio.setNombres("Socio Estado");
        socio.setApellidos("Prueba " + token.substring(0, 8));
        socio.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        socio.setCelular("999999999");
        socio.setCorreo("socio-" + token + "@example.test");

        SocioRespuesta registrado = socioService.registrar(socio);
        sociosCreados.add(registrado.idSocio());
        return registrado;
    }

    private org.springframework.test.web.servlet.ResultActions cambiarEstado(
            Long idSocio, String operacion, String motivo) throws Exception {
        return mockMvc.perform(patch("/api/socios/{id}/" + operacion, idSocio)
                .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoMotivo(motivo)));
    }

    private List<Auditoria> auditoriasDelSocio(Long idSocio) {
        String idRegistro = idSocio.toString();
        return auditoriaRepository.findAll().stream()
                .filter(auditoria -> "SOCIO".equals(auditoria.getEntidad())
                        && idRegistro.equals(auditoria.getIdRegistro()))
                .toList();
    }

    private void assertAuditoriaPresente(List<Auditoria> auditorias,
            Long idSocio, String accion, String motivo,
            String estadoAnterior, String estadoNuevo) {
        Auditoria auditoria = auditorias.stream()
                .filter(registro -> accion.equals(registro.getAccion()))
                .findFirst()
                .orElseThrow();
        verificarAuditoria(auditoria, idSocio, accion, motivo,
                estadoAnterior, estadoNuevo);
    }

    private void verificarAuditoria(Auditoria auditoria, Long idSocio,
            String accion, String motivo, String estadoAnterior, String estadoNuevo) {
        assertEquals(actor.getIdUsuario(), auditoria.getIdUsuario());
        assertEquals(accion, auditoria.getAccion());
        assertEquals("SOCIO", auditoria.getEntidad());
        assertEquals(idSocio.toString(), auditoria.getIdRegistro());
        assertEquals(motivo, auditoria.getDetalle().get("motivo"));
        assertEquals(estadoAnterior, auditoria.getDetalle().get("estadoAnterior"));
        assertEquals(estadoNuevo, auditoria.getDetalle().get("estadoNuevo"));
        assertNotNull(auditoria.getFechaHora(),
                "fecha_hora debe ser generada por MySQL al insertar la auditoria.");
    }

    private static String cuerpoMotivo(String motivo) {
        return "{\"motivo\":\"" + motivo + "\"}";
    }
}
