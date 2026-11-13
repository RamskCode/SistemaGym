package com.gimnasio.sistemagym.integration.mysql;

import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import com.gimnasio.sistemagym.repository.AsistenciaLaboralRepository;
import com.gimnasio.sistemagym.service.AsistenciaLaboralService;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the limited reception cancellation rule against disposable MySQL 8.4. */
class AnulacionRecepcionistaMySql84IT extends MySql84IntegrationTestSupport {
    private static final String RECEPTION_EMAIL = "anulacion-it-recepcion@example.invalid";
    private static final String ADMIN_EMAIL = "anulacion-it-admin@example.invalid";
    private static final String TRAINER_EMAIL = "anulacion-it-trainer@example.invalid";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private AsistenciaLaboralService attendanceService;
    @Autowired private AsistenciaLaboralRepository attendanceRepository;

    private long receptionistId;
    private long adminId;
    private long trainerId;

    @BeforeEach
    void createFictionalUsers() {
        insertRole("RECEPCIONISTA");
        insertRole("ADMINISTRADOR");
        insertRole("ENTRENADOR");
        receptionistId = insertUser("RECEPCIONISTA", "Recepción", "IT", RECEPTION_EMAIL);
        adminId = insertUser("ADMINISTRADOR", "Admin", "IT", ADMIN_EMAIL);
        trainerId = insertUser("ENTRENADOR", "Entrenador", "IT", TRAINER_EMAIL);
    }

    @AfterEach
    void removeFictionalRecords() {
        jdbc.update("DELETE FROM auditorias_asistencia_laboral WHERE id_usuario_actor IN (?, ?)",
                receptionistId, adminId);
        jdbc.update("DELETE e FROM eventos_asistencia_laboral e JOIN asistencias_laborales a "
                        + "ON a.id_asistencia = e.id_asistencia WHERE a.id_usuario = ?",
                trainerId);
        jdbc.update("DELETE FROM asistencias_laborales WHERE id_usuario = ? OR "
                        + "id_registrado_entrada IN (?, ?) OR id_registrado_salida IN (?, ?)",
                trainerId, receptionistId, adminId, receptionistId, adminId);
        jdbc.update("DELETE FROM usuarios WHERE correo IN (?, ?, ?)",
                RECEPTION_EMAIL, ADMIN_EMAIL, TRAINER_EMAIL);
    }

    @Test
    void cancelsOwnUncorrectedRecordButCannotCancelOneAfterAdminCorrection() {
        var own = attendanceService.registrarEntrada(trainerId, receptionistId);
        var cancelled = attendanceService.anularAsistencia(own.getIdAsistencia(),
                "Marcación duplicada", RECEPTION_EMAIL);
        assertEquals(EstadoAsistenciaLaboral.ANULADA, cancelled.getEstado());

        var corrected = attendanceService.registrarEntrada(trainerId, receptionistId);
        Instant correctedEntry = corrected.getFechaEntradaUtc().toInstant(ZoneOffset.UTC)
                .plusSeconds(1);
        attendanceService.corregirAsistencia(corrected.getIdAsistencia(), correctedEntry,
                null, "Corrección administrativa", ADMIN_EMAIL);

        assertThrows(AccessDeniedException.class, () -> attendanceService.anularAsistencia(
                corrected.getIdAsistencia(), "Anulación posterior a corrección", RECEPTION_EMAIL));
        assertEquals(EstadoAsistenciaLaboral.ACTIVA,
                attendanceRepository.findById(corrected.getIdAsistencia()).orElseThrow().getEstado());
    }

    private void insertRole(String role) {
        jdbc.update("INSERT INTO roles (nombre, descripcion) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE nombre = VALUES(nombre)", role, "Fixture de IT aislado");
    }

    private long insertUser(String role, String first, String last, String email) {
        Long roleId = jdbc.queryForObject("SELECT id_rol FROM roles WHERE nombre = ?", Long.class, role);
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, contrasena_hash) "
                        + "VALUES (?, ?, ?, ?, 'hash-it')", roleId, first, last, email);
        return jdbc.queryForObject("SELECT id_usuario FROM usuarios WHERE correo = ?", Long.class, email);
    }
}
