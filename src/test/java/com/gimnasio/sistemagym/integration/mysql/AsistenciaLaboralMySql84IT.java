package com.gimnasio.sistemagym.integration.mysql;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralRepository;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsistenciaLaboralMySql84IT extends MySql84IntegrationTestSupport {

    private static final String EMPLEADO = "asistencia-persist-it-empleado@example.invalid";
    private static final String REGISTRADOR = "asistencia-persist-it-registrador@example.invalid";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AsistenciaLaboralRepository asistenciaLaboralRepository;

    private long idEmpleado;
    private long idRegistrador;

    @BeforeEach
    void crearPersonalAislado() {
        jdbcTemplate.update("INSERT INTO roles (nombre, descripcion) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre)",
                "ASISTENCIA_PERSIST_IT", "Rol aislado de integración");
        long idRol = jdbcTemplate.queryForObject(
                "SELECT id_rol FROM roles WHERE nombre = ?", Long.class,
                "ASISTENCIA_PERSIST_IT");
        jdbcTemplate.update("INSERT INTO usuarios "
                        + "(id_rol, nombres, apellidos, correo, contrasena_hash) "
                        + "VALUES (?, 'Empleado', 'Prueba', ?, 'hash-it')",
                idRol, EMPLEADO);
        jdbcTemplate.update("INSERT INTO usuarios "
                        + "(id_rol, nombres, apellidos, correo, contrasena_hash) "
                        + "VALUES (?, 'Registrador', 'Prueba', ?, 'hash-it')",
                idRol, REGISTRADOR);
        idEmpleado = idUsuario(EMPLEADO);
        idRegistrador = idUsuario(REGISTRADOR);
        jdbcTemplate.update("UPDATE usuarios SET estado = 'INACTIVO' WHERE id_usuario = ?",
                idEmpleado);
    }

    @AfterEach
    void limpiarPersonalAislado() {
        jdbcTemplate.update("DELETE e FROM eventos_asistencia_laboral e "
                        + "JOIN asistencias_laborales a ON a.id_asistencia = e.id_asistencia "
                        + "WHERE a.id_usuario IN (?, ?) OR e.id_usuario_registrador IN "
                        + "(SELECT id_usuario FROM usuarios WHERE correo IN (?, ?))",
                idEmpleado, idRegistrador, EMPLEADO, REGISTRADOR);
        jdbcTemplate.update("DELETE FROM asistencias_laborales WHERE id_usuario IN (?, ?) "
                        + "OR id_registrado_entrada IN (?, ?) OR id_registrado_salida IN (?, ?)",
                idEmpleado, idRegistrador, idEmpleado, idRegistrador, idEmpleado, idRegistrador);
        jdbcTemplate.update("DELETE FROM usuarios WHERE correo IN (?, ?)", EMPLEADO, REGISTRADOR);
    }

    @Test
    void indiceUnicoImpideDosSesionesAbiertasDelMismoUsuario() {
        insertarAsistenciaAbierta();

        assertThrows(DataIntegrityViolationException.class,
                this::insertarAsistenciaAbierta);
    }

    @Test
    void checkImpideSalidaAnteriorALaEntrada() {
        UncategorizedSQLException excepcion = assertThrows(UncategorizedSQLException.class,
                () -> jdbcTemplate.update(
                "INSERT INTO asistencias_laborales "
                        + "(id_usuario, fecha_entrada_utc, fecha_salida_utc, fecha_laboral, "
                        + "id_registrado_entrada, id_registrado_salida) "
                        + "VALUES (?, '2026-11-05 10:00:00.000001', "
                        + "'2026-11-05 10:00:00.000000', '2026-11-05', ?, ?)",
                idEmpleado, idRegistrador, idRegistrador));

        assertAll(
                () -> assertEquals("HY000", excepcion.getSQLException().getSQLState()),
                () -> assertEquals(3819, excepcion.getSQLException().getErrorCode()),
                () -> assertTrue(excepcion.getSQLException().getMessage().contains(
                        "chk_asistencias_laborales_salida_posterior")));
    }

    @Test
    void datetimesAlmacenanMicrosegundos() {
        insertarAsistenciaAbierta();
        String almacenado = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(fecha_entrada_utc, '%Y-%m-%d %H:%i:%s.%f') "
                        + "FROM asistencias_laborales WHERE id_usuario = ?",
                String.class, idEmpleado);

        assertTrue(almacenado.endsWith(".123456"));
    }

    @Test
    void historialPaginadoIncluyeNombreAunqueElRolNoSeaSeleccionable() {
        insertarAsistenciaAbierta();

        var resultado = asistenciaLaboralRepository.consultarHistorial(
                idEmpleado, null, null, null, PageRequest.of(0, 20));

        assertEquals(1, resultado.getTotalElements());
        assertEquals("Empleado", resultado.getContent().getFirst().getNombresEmpleado());
        assertEquals("Prueba", resultado.getContent().getFirst().getApellidosEmpleado());
        assertEquals("ASISTENCIA_PERSIST_IT", resultado.getContent().getFirst().getRolEmpleado());
        var empleadosConHistorial = asistenciaLaboralRepository.listarEmpleadosConHistorial();
        assertEquals(1, empleadosConHistorial.size());
        assertEquals(idEmpleado, empleadosConHistorial.getFirst().getIdEmpleado());
        assertEquals("ASISTENCIA_PERSIST_IT", empleadosConHistorial.getFirst().getRol());
    }

    private void insertarAsistenciaAbierta() {
        jdbcTemplate.update("INSERT INTO asistencias_laborales "
                        + "(id_usuario, fecha_entrada_utc, fecha_laboral, id_registrado_entrada) "
                        + "VALUES (?, '2026-11-05 04:30:00.123456', '2026-11-04', ?)",
                idEmpleado, idRegistrador);
    }

    private long idUsuario(String correo) {
        return jdbcTemplate.queryForObject(
                "SELECT id_usuario FROM usuarios WHERE correo = ?", Long.class, correo);
    }
}
