package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@Execution(ExecutionMode.SAME_THREAD)
class MySqlSchemaConstraintsMySql84IT extends MySql84IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void mysqlAplicaRestriccionesFisicasDelEsquemaDePruebas() {
        verificaClaveForanea();
        verificaUnicidad();
        verificaCheck();
        verificaNotNull();
        verificaEnum();
        verificaJson();
        verificaUnsigned();
        verificaDefaultsYAutoIncrement();
    }

    private void verificaClaveForanea() {
        String correo = correoUnico();
        try {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO usuarios "
                            + "(id_rol, nombres, apellidos, correo, contrasena_hash) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    9876543210L, "Fixture", "FK", correo, "fixture"));
        } finally {
            jdbcTemplate.update("DELETE FROM usuarios WHERE correo = ?", correo);
        }
    }

    private void verificaUnicidad() {
        String nombre = "IT_UQ_" + tokenCorto();
        try {
            jdbcTemplate.update("INSERT INTO roles (nombre) VALUES (?)", nombre);
            assertThrows(DataAccessException.class,
                    () -> jdbcTemplate.update(
                            "INSERT INTO roles (nombre) VALUES (?)", nombre));
        } finally {
            jdbcTemplate.update("DELETE FROM roles WHERE nombre = ?", nombre);
        }
    }

    private void verificaCheck() {
        String codigo = "IT-CHECK-" + tokenCorto();
        try {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO socios (codigo_socio, nombres, apellidos, estado) "
                            + "VALUES (?, ?, ?, ?)",
                    codigo, "Fixture", "Check", "SUSPENDIDO"));
        } finally {
            jdbcTemplate.update("DELETE FROM socios WHERE codigo_socio = ?", codigo);
        }
    }

    private void verificaNotNull() {
        String codigo = "IT-NULL-" + tokenCorto();
        try {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO socios (codigo_socio, nombres, apellidos) "
                            + "VALUES (?, ?, ?)",
                    codigo, null, "NotNull"));
        } finally {
            jdbcTemplate.update("DELETE FROM socios WHERE codigo_socio = ?", codigo);
        }
    }

    private void verificaEnum() {
        String token = token();
        Long idRol = null;
        Long idUsuario = null;
        Long idSocio = null;
        Long idPlan = null;
        Long idPago = null;
        Long idMembresia = null;
        String codigoSocio = "IT-ENUM-" + tokenCorto();
        try {
            idRol = insertarConId(
                    "INSERT INTO roles (nombre) VALUES (?)", "IT_ENUM_" + tokenCorto());
            idUsuario = insertarConId(
                    "INSERT INTO usuarios "
                            + "(id_rol, nombres, apellidos, correo, contrasena_hash) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    idRol, "Fixture", "Enum", correoUnico(), "fixture");
            idSocio = insertarConId(
                    "INSERT INTO socios (codigo_socio, nombres, apellidos) "
                            + "VALUES (?, ?, ?)",
                    codigoSocio, "Fixture", "Enum");
            idPlan = insertarConId(
                    "INSERT INTO planes (nombre, precio) VALUES (?, ?)",
                    "IT_ENUM_" + tokenCorto(), new BigDecimal("10.00"));
            idPago = insertarConId(
                    "INSERT INTO pagos (id_usuario, monto, metodo_pago) "
                            + "VALUES (?, ?, ?)",
                    idUsuario, new BigDecimal("10.00"), "EFECTIVO");
            idMembresia = insertarConId(
                    "INSERT INTO membresias "
                            + "(id_socio, id_plan, precio_acordado, fecha_inicio, "
                            + "fecha_vencimiento) VALUES (?, ?, ?, ?, ?)",
                    idSocio, idPlan, new BigDecimal("10.00"),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            Long pagoFixture = idPago;
            Long membresiaFixture = idMembresia;
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO aplicaciones_pago_membresia "
                            + "(id_pago, id_membresia, monto_aplicado, tipo_aplicacion) "
                            + "VALUES (?, ?, ?, ?)",
                    pagoFixture, membresiaFixture, new BigDecimal("1.00"), "INVALIDO"));
        } finally {
            if (idPago != null || idMembresia != null) {
                jdbcTemplate.update(
                        "DELETE FROM aplicaciones_pago_membresia "
                                + "WHERE id_pago = ? OR id_membresia = ?",
                        idPago, idMembresia);
            }
            if (idMembresia != null) {
                jdbcTemplate.update("DELETE FROM membresias WHERE id_membresia = ?",
                        idMembresia);
            }
            if (idPago != null) {
                jdbcTemplate.update("DELETE FROM pagos WHERE id_pago = ?", idPago);
            }
            if (idSocio != null) {
                jdbcTemplate.update("DELETE FROM socios WHERE id_socio = ?", idSocio);
            }
            if (idPlan != null) {
                jdbcTemplate.update("DELETE FROM planes WHERE id_plan = ?", idPlan);
            }
            if (idUsuario != null) {
                jdbcTemplate.update("DELETE FROM usuarios WHERE id_usuario = ?", idUsuario);
            }
            if (idRol != null) {
                jdbcTemplate.update("DELETE FROM roles WHERE id_rol = ?", idRol);
            }
        }
    }

    private void verificaJson() {
        String idRegistro = "IT-JSON-" + tokenCorto();
        try {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO auditoria (accion, entidad, id_registro, detalle) "
                            + "VALUES (?, ?, ?, ?)",
                    "PRUEBA_JSON", "PRUEBA", idRegistro, "{json-invalido"));
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM auditoria WHERE entidad = 'PRUEBA' AND id_registro = ?",
                    idRegistro);
        }
    }

    private void verificaUnsigned() {
        String codigo = "IT-UNSIGNED-" + tokenCorto();
        try {
            assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                    "INSERT INTO socios (id_socio, codigo_socio, nombres, apellidos) "
                            + "VALUES (-1, ?, ?, ?)",
                    codigo, "Fixture", "Unsigned"));
        } finally {
            jdbcTemplate.update("DELETE FROM socios WHERE codigo_socio = ?", codigo);
        }
    }

    private void verificaDefaultsYAutoIncrement() {
        String nombreRol1 = "IT_AI_A_" + tokenCorto();
        String nombreRol2 = "IT_AI_B_" + tokenCorto();
        String codigoSocio = "IT-DEF-" + tokenCorto();
        String nombrePlan = "IT_DEFAULT_" + tokenCorto();
        Long idSocio = null;
        Long idPlan = null;
        try {
            Long idRol1 = insertarConId(
                    "INSERT INTO roles (nombre) VALUES (?)", nombreRol1);
            Long idRol2 = insertarConId(
                    "INSERT INTO roles (nombre) VALUES (?)", nombreRol2);
            assertTrue(idRol1 > 0);
            assertTrue(idRol2 > idRol1,
                    "AUTO_INCREMENT debe asignar identificadores crecientes.");

            idSocio = insertarConId(
                    "INSERT INTO socios (codigo_socio, nombres, apellidos) "
                            + "VALUES (?, ?, ?)",
                    codigoSocio, "Fixture", "Default");
            assertTrue(idSocio > 0,
                    "AUTO_INCREMENT debe generar el identificador del socio.");
            assertEquals("ACTIVO", jdbcTemplate.queryForObject(
                    "SELECT estado FROM socios WHERE id_socio = ?", String.class, idSocio));
            assertNotNull(jdbcTemplate.queryForObject(
                    "SELECT fecha_registro FROM socios WHERE id_socio = ?",
                    java.time.LocalDateTime.class, idSocio));

            idPlan = insertarConId(
                    "INSERT INTO planes (nombre, precio) VALUES (?, ?)",
                    nombrePlan, new BigDecimal("10.00"));
            assertTrue(idPlan > 0);
            assertEquals(30, jdbcTemplate.queryForObject(
                    "SELECT duracion_dias FROM planes WHERE id_plan = ?",
                    Integer.class, idPlan));
            assertEquals("ACTIVO", jdbcTemplate.queryForObject(
                    "SELECT estado FROM planes WHERE id_plan = ?", String.class, idPlan));
            assertNotNull(jdbcTemplate.queryForObject(
                    "SELECT fecha_creacion FROM planes WHERE id_plan = ?",
                    java.time.LocalDateTime.class, idPlan));
        } finally {
            if (idSocio != null) {
                jdbcTemplate.update("DELETE FROM socios WHERE id_socio = ?", idSocio);
            }
            if (idPlan != null) {
                jdbcTemplate.update("DELETE FROM planes WHERE id_plan = ?", idPlan);
            }
            jdbcTemplate.update("DELETE FROM roles WHERE nombre IN (?, ?)",
                    nombreRol1, nombreRol2);
        }
    }

    private Long insertarConId(String sql, Object... valores) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < valores.length; i++) {
                statement.setObject(i + 1, valores[i]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        assertNotNull(key, "MySQL debe devolver la clave AUTO_INCREMENT generada.");
        return key.longValue();
    }

    private static String correoUnico() {
        return "it-" + token() + "@example.test";
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String tokenCorto() {
        return token().substring(0, 12);
    }
}
