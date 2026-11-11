package com.gimnasio.sistemagym.integration.mysql;

import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@AutoConfigureMockMvc
class PaseDiarioReingresosMySql84IT extends MySql84IntegrationTestSupport {
    @DynamicPropertySource
    static void habilitarEsquemaV2(DynamicPropertyRegistry registry) {
        registry.add("sistemagym.pases-diarios.esquema-v2-habilitado", () -> true);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SocioRepository socios;
    @Autowired private PlanRepository planes;

    private Usuario actor;
    private Socio persona;
    private Plan plan;

    @BeforeEach
    void prepararIdentidades() {
        Rol rol = roles.findByNombreIgnoreCase("RECEPCIONISTA").orElseGet(() -> {
            Rol nuevo = new Rol(); nuevo.setNombre("RECEPCIONISTA"); return roles.saveAndFlush(nuevo);
        });
        actor = new Usuario(); actor.setRol(rol); actor.setNombres("Recepción");
        actor.setApellidos("Prueba"); actor.setCorreo("pase-" + java.util.UUID.randomUUID() + "@example.test");
        actor.setContrasenaHash("hash-de-prueba"); actor.setEstado("ACTIVO");
        actor = usuarios.saveAndFlush(actor);
        persona = new Socio(); persona.setCodigoSocio("PASE-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        persona.setDni("76543210"); persona.setTipoDocumento("DNI"); persona.setNumeroDocumento("76543210");
        persona.setNombres("Visitante"); persona.setApellidos("Recurrente"); persona.setEstado("ACTIVO");
        persona = socios.saveAndFlush(persona);
    }

    @AfterEach
    void limpiarSoloFixturesPropios() {
        if (actor == null) return;
        jdbc.update("DELETE FROM operaciones_pago_idempotentes WHERE id_usuario = ?", actor.getIdUsuario());
        jdbc.update("DELETE FROM ingresos WHERE id_usuario = ? AND id_pase_origen IS NOT NULL", actor.getIdUsuario());
        jdbc.update("DELETE FROM ingresos WHERE id_usuario = ?", actor.getIdUsuario());
        if (persona != null) {
            jdbc.update("DELETE FROM aplicaciones_pago_membresia WHERE id_membresia IN (SELECT id_membresia FROM membresias WHERE id_socio = ?) OR id_pago IN (SELECT id_pago FROM pagos WHERE id_usuario = ?)",
                    persona.getIdSocio(), actor.getIdUsuario());
            jdbc.update("DELETE FROM membresias WHERE id_socio = ?", persona.getIdSocio());
        }
        jdbc.update("DELETE FROM pagos WHERE id_usuario = ?", actor.getIdUsuario());
        if (persona != null) jdbc.update("DELETE FROM socios WHERE id_socio = ?", persona.getIdSocio());
        if (plan != null) planes.deleteById(plan.getIdPlan());
        jdbc.update("DELETE FROM usuarios WHERE id_usuario = ?", actor.getIdUsuario());
    }

    @Test
    void unPagoPermiteReingresosDelDiaYElRetryNoDuplicaElCobro() throws Exception {
        String primeraClave = java.util.UUID.randomUUID().toString();
        String reingresoClave = java.util.UUID.randomUUID().toString();
        String primerBody = cuerpo(primeraClave);
        String reingresoBody = cuerpo(reingresoClave);

        mockMvc.perform(post("/api/ingresos").with(user(actor.getCorreo()).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON).content(primerBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paseDiarioCobrado").value(true));
        Long ventaId = jdbc.queryForObject("SELECT id_ingreso FROM ingresos WHERE clave_idempotencia = ?", Long.class, primeraClave);

        mockMvc.perform(post("/api/ingresos").with(user(actor.getCorreo()).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON).content(reingresoBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resultado").value("REINGRESO_AUTORIZADO"))
                .andExpect(jsonPath("$.paseDiarioCobrado").value(false));
        mockMvc.perform(post("/api/ingresos").with(user(actor.getCorreo()).roles("RECEPCIONISTA"))
                        .contentType(APPLICATION_JSON).content(reingresoBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resultado").value("IDEMPOTENTE"));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ingresos WHERE id_socio = ?", Integer.class, persona.getIdSocio()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM pagos WHERE id_usuario = ?", Integer.class, actor.getIdUsuario()));
        assertEquals(ventaId, jdbc.queryForObject("SELECT id_pase_origen FROM ingresos WHERE clave_idempotencia = ?", Long.class, reingresoClave));

        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        mockMvc.perform(get("/api/reportes/resumen")
                        .param("desde", hoy).param("hasta", hoy)
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresosConfirmados").value(8.00))
                .andExpect(jsonPath("$.pagosConfirmados").value(1))
                .andExpect(jsonPath("$.ingresosVisitantes").value(2));

        mockMvc.perform(get("/api/ingresos/pase-diario/socio/{id}", persona.getIdSocio())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cobradoEnEsteAcceso").value(false))
                .andExpect(jsonPath("$[0].idPaseDiario").value(ventaId))
                .andExpect(jsonPath("$[0].idPago").isNumber())
                .andExpect(jsonPath("$[1].cobradoEnEsteAcceso").value(true));

        plan = new Plan();
        plan.setNombre("IT_PASE_" + java.util.UUID.randomUUID().toString().substring(0, 8));
        plan.setPrecio(new java.math.BigDecimal("200.00"));
        plan.setDuracionDias(30);
        plan.setEstado("ACTIVO");
        plan = planes.saveAndFlush(plan);
        mockMvc.perform(post("/api/membresias")
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .with(user(actor.getCorreo()).roles("ADMINISTRADOR"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"idSocio\":" + persona.getIdSocio() + ",\"idPlan\":"
                                + plan.getIdPlan() + ",\"montoPrimerPago\":200.00,\"metodoPago\":\"EFECTIVO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMembresia").isNumber());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM membresias WHERE id_socio = ?", Integer.class, persona.getIdSocio()));
    }

    private String cuerpo(String key) {
        return "{\"tipoDocumento\":\"DNI\",\"numeroDocumento\":\"76543210\",\"idSocio\":"
                + persona.getIdSocio() + ",\"metodoPago\":\"EFECTIVO\",\"claveIdempotencia\":\"" + key + "\"}";
    }
}
