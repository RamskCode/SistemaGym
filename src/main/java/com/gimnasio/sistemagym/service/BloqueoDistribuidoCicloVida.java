package com.gimnasio.sistemagym.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Session advisory lock held on one connection throughout the scheduled run. */
@Component
public class BloqueoDistribuidoCicloVida {

    private static final String NOMBRE_BLOQUEO = "sistemagym:membresias:ciclo-vida";
    private final JdbcTemplate jdbcTemplate;

    public BloqueoDistribuidoCicloVida(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public <T> T ejecutarSiDisponible(Callable<T> trabajo) throws Exception {
        return jdbcTemplate.execute((Connection conexion) -> {
            String motor = conexion.getMetaData().getDatabaseProductName();
            boolean postgres = motor.toLowerCase(java.util.Locale.ROOT).contains("postgresql");
            if (!postgres && !motor.toLowerCase(java.util.Locale.ROOT).contains("mysql")) {
                throw new SQLException("Motor de base de datos no soportado para advisory locks: " + motor);
            }
            boolean adquirido = adquirir(conexion, postgres);
            if (!adquirido) return null;
            try {
                return trabajo.call();
            } catch (Exception error) {
                throw new ErrorTrabajo(error);
            } finally {
                liberar(conexion, postgres);
            }
        });
    }

    private boolean adquirir(Connection conexion, boolean postgres) throws SQLException {
        String sql = postgres
                ? "SELECT pg_try_advisory_lock(?, ?)"
                : "SELECT GET_LOCK(?, 0)";
        try (PreparedStatement statement = conexion.prepareStatement(sql)) {
            if (postgres) {
                statement.setInt(1, 1_936_728_421);
                statement.setInt(2, 2);
            } else {
                statement.setString(1, NOMBRE_BLOQUEO);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && (postgres ? result.getBoolean(1) : result.getInt(1) == 1);
            }
        }
    }

    private void liberar(Connection conexion, boolean postgres) {
        String sql = postgres
                ? "SELECT pg_advisory_unlock(?, ?)"
                : "SELECT RELEASE_LOCK(?)";
        try (PreparedStatement statement = conexion.prepareStatement(sql)) {
            if (postgres) {
                statement.setInt(1, 1_936_728_421);
                statement.setInt(2, 2);
            } else {
                statement.setString(1, NOMBRE_BLOQUEO);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new SQLException("El motor no confirmó la liberación del advisory lock.");
                }
            }
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo liberar el bloqueo distribuido del ciclo de vida.", error);
        }
    }

    private static final class ErrorTrabajo extends RuntimeException {
        private ErrorTrabajo(Exception cause) { super(cause); }
    }
}
