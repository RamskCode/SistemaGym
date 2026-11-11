package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CambioRolNutricionistaMySql84IT extends MySql84IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Order(2)
    void autenticaConBcryptYRechazaClaveIncorrectaUsuarioInexistenteEInactivo()
            throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id_rol FROM roles WHERE nombre = ?", Long.class, "NUTRICIONISTA");
        if (roleId == null) {
            jdbcTemplate.update("INSERT INTO roles (nombre) VALUES (?)", "NUTRICIONISTA");
            roleId = jdbcTemplate.queryForObject(
                    "SELECT id_rol FROM roles WHERE nombre = ?",
                    Long.class, "NUTRICIONISTA");
        }
        String password = "StrongTestPassword!42";
        String hash = passwordEncoder.encode(password);
        String activeEmail = "activo-" + suffix + "@example.test";
        String inactiveEmail = "inactivo-" + suffix + "@example.test";
        jdbcTemplate.update("""
                INSERT INTO usuarios
                  (id_rol, nombres, apellidos, correo, contrasena_hash, estado)
                VALUES (?, 'Nutri', 'Activo', ?, ?, 'ACTIVO'),
                       (?, 'Nutri', 'Inactivo', ?, ?, 'INACTIVO')
                """, roleId, activeEmail, hash, roleId, inactiveEmail, hash);

        String token = tokenPara(activeEmail, password);
        org.junit.jupiter.api.Assertions.assertNotNull(token);
        mockMvc.perform(get("/api/socios").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/usuarios").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/membresias").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ingresos").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(activeEmail, "wrong-password")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson("missing-" + suffix + "@example.test", password)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content(loginJson(inactiveEmail, password)))
                .andExpect(status().isUnauthorized());
    }

    private String tokenPara(String correo, String contrasena) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(loginJson(correo, contrasena)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String loginJson(String correo, String contrasena) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("correo", correo, "contrasena", contrasena));
    }

    @Test
    @Order(1)
    void renombrarFilaDeRolConservaCuentasHashesYReferenciasHistoricas() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String oldHash1 = "$2a$10$old-hash-fixture-account-one";
        String oldHash2 = "$2a$10$old-hash-fixture-account-two";
        jdbcTemplate.update("INSERT INTO roles (nombre) VALUES (?)", "ENTRENADOR");
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id_rol FROM roles WHERE nombre = ?", Long.class, "ENTRENADOR");
        jdbcTemplate.update("""
                INSERT INTO usuarios
                  (id_rol, nombres, apellidos, correo, contrasena_hash, estado)
                VALUES (?, 'Persona', 'Uno', ?, ?, 'ACTIVO'),
                       (?, 'Persona', 'Dos', ?, ?, 'INACTIVO')
                """, roleId, "uno-" + suffix + "@example.test", oldHash1,
                roleId, "dos-" + suffix + "@example.test", oldHash2);
        Long firstUserId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE correo = ?",
                Long.class, "uno-" + suffix + "@example.test");
        Long secondUserId = jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE correo = ?",
                Long.class, "dos-" + suffix + "@example.test");
        jdbcTemplate.update("""
                INSERT INTO auditoria (id_usuario, accion, entidad, id_registro)
                VALUES (?, 'ROLE_MIGRATION_TEST', 'USUARIO', ?),
                       (?, 'ROLE_MIGRATION_TEST', 'USUARIO', ?)
                """, firstUserId, firstUserId.toString(),
                secondUserId, secondUserId.toString());

        int updated = jdbcTemplate.update(
                "UPDATE roles SET nombre = 'NUTRICIONISTA' WHERE id_rol = ? AND nombre = ?",
                roleId, "ENTRENADOR");

        assertEquals(1, updated);
        assertEquals("NUTRICIONISTA", jdbcTemplate.queryForObject(
                "SELECT nombre FROM roles WHERE id_rol = ?", String.class, roleId));
        var preserved = jdbcTemplate.query("""
                SELECT id_usuario, id_rol, correo, contrasena_hash, estado
                FROM usuarios WHERE id_rol = ? ORDER BY id_usuario
                """, (rs, row) -> new Object[]{rs.getLong("id_usuario"),
                        rs.getLong("id_rol"), rs.getString("correo"),
                        rs.getString("contrasena_hash"), rs.getString("estado")}, roleId);

        assertEquals(2, preserved.size());
        assertEquals(roleId, preserved.get(0)[1]);
        assertEquals(roleId, preserved.get(1)[1]);
        assertEquals(oldHash1, preserved.get(0)[3]);
        assertEquals(oldHash2, preserved.get(1)[3]);
        assertEquals("ACTIVO", preserved.get(0)[4]);
        assertEquals("INACTIVO", preserved.get(1)[4]);
        assertEquals("uno-" + suffix + "@example.test", preserved.get(0)[2]);
        assertEquals("dos-" + suffix + "@example.test", preserved.get(1)[2]);
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM auditoria
                WHERE id_usuario IN (?, ?) AND accion = 'ROLE_MIGRATION_TEST'
                """, Integer.class, firstUserId, secondUserId));
    }
}
