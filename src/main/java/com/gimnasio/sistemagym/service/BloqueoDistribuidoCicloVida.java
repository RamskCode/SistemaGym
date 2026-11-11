package com.gimnasio.sistemagym.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** MySQL advisory lock held on one connection throughout the scheduled run. */
@Component
public class BloqueoDistribuidoCicloVida {

    private static final String NOMBRE_BLOQUEO = "sistemagym:membresias:ciclo-vida";
    private final JdbcTemplate jdbcTemplate;

    public BloqueoDistribuidoCicloVida(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public <T> T ejecutarSiDisponible(Callable<T> trabajo) throws Exception {
        return jdbcTemplate.execute((Connection conexion) -> {
            boolean adquirido = adquirir(conexion);
            if (!adquirido) return null;
            try {
                return trabajo.call();
            } catch (Exception error) {
                throw new ErrorTrabajo(error);
            } finally {
                liberar(conexion);
            }
        });
    }

    private boolean adquirir(Connection conexion) throws SQLException {
        try (PreparedStatement statement = conexion.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, NOMBRE_BLOQUEO);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1;
            }
        }
    }

    private void liberar(Connection conexion) {
        try (PreparedStatement statement = conexion.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, NOMBRE_BLOQUEO);
            statement.execute();
        } catch (SQLException error) {
            throw new IllegalStateException("No se pudo liberar el bloqueo distribuido del ciclo de vida.", error);
        }
    }

    private static final class ErrorTrabajo extends RuntimeException {
        private ErrorTrabajo(Exception cause) { super(cause); }
    }
}
