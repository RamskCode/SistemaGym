package com.gimnasio.sistemagym.integration.mysql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies only the initialized schema and JPA context, without business writes. */
class SchemaBootstrapMySql84IT extends MySql84IntegrationTestSupport {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "auditoria",
            "usuarios",
            "roles",
            "socios",
            "planes",
            "membresias",
            "pagos",
            "aplicaciones_pago_membresia",
            "operaciones_pago_idempotentes",
            "entradas_diarias",
            "contactos_promocionales",
            "asignaciones_entrenador",
            "asistencias",
            "asistencias_laborales",
            "eventos_asistencia_laboral",
            "auditorias_asistencia_laboral",
            "elementos_revision",
            "checklists_sala",
            "detalles_checklist",
            "evaluaciones_nutricionales",
            "reportes_incidencias",
            "historial_reportes_incidencia",
            "ingresos",
            "configuraciones",
            "credenciales_qr",
            "notificaciones",
            "invitaciones_usuario",
            "recuperaciones_contrasena",
            "sesiones_refresh");

    private static final int EXPECTED_FOREIGN_KEYS = 47;

    @Autowired
    private DataSource dataSource;

    @Test
    void creaLasTablasYLasClavesForaneasEnElContenedor() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> tables = obtenerTablas(metadata, connection.getCatalog());

            assertEquals(EXPECTED_TABLES, tables);
            assertEquals(EXPECTED_FOREIGN_KEYS,
                    contarClavesForaneas(metadata, connection.getCatalog(), tables));
        }
    }

    private static Set<String> obtenerTablas(DatabaseMetaData metadata,
            String catalog) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet result = metadata.getTables(
                catalog, null, "%", new String[] {"TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private static int contarClavesForaneas(DatabaseMetaData metadata,
            String catalog, Set<String> tables) throws SQLException {
        int count = 0;
        for (String table : tables) {
            try (ResultSet result = metadata.getImportedKeys(catalog, null, table)) {
                while (result.next()) {
                    count++;
                }
            }
        }
        return count;
    }
}
