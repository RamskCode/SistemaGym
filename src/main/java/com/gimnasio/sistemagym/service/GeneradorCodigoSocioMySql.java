package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.function.Function;

@Component
public class GeneradorCodigoSocioMySql implements GeneradorCodigoSocio {

    private static final Logger log = LoggerFactory.getLogger(GeneradorCodigoSocioMySql.class);
    private static final String NOMBRE_BLOQUEO = "sistemagym:socios:codigo";
    private static final int TIEMPO_ESPERA_SEGUNDOS = 15;
    private static final String SQL_OBTENER_BLOQUEO = "SELECT GET_LOCK(?, ?)";
    private static final String SQL_LIBERAR_BLOQUEO = "SELECT RELEASE_LOCK(?)";

    private final JdbcTemplate jdbcTemplate;
    private final SocioRepository socioRepository;
    private final HikariDataSource hikariDataSource;

    public GeneradorCodigoSocioMySql(JdbcTemplate jdbcTemplate,
            SocioRepository socioRepository, HikariDataSource hikariDataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.socioRepository = socioRepository;
        this.hikariDataSource = hikariDataSource;
    }

    @Override
    public Socio crearConSiguienteCodigo(Function<String, Socio> crearSocio) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "La generación del código requiere una transacción activa.");
        }

        return jdbcTemplate.execute((ConnectionCallback<Socio>) connection -> {
            final boolean bloqueoAdquirido;
            try {
                bloqueoAdquirido = obtenerBloqueo(connection);
            } catch (SQLException | RuntimeException errorAdquisicion) {
                try {
                    liberarBloqueo(connection);
                } catch (RuntimeException errorLiberacion) {
                    errorAdquisicion.addSuppressed(errorLiberacion);
                }
                throw errorAdquisicion;
            }

            if (!bloqueoAdquirido) {
                throw new ConflictoException(
                        "No se pudo reservar un código de socio. Reintente la operación.");
            }

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            liberarBloqueo(connection);
                        }
                    });

            Integer ultimoNumero = socioRepository.obtenerUltimoNumeroCodigo();
            int siguienteNumero = ultimoNumero == null ? 1 : ultimoNumero + 1;
            String codigo = String.format(Locale.ROOT, "SOC-%04d", siguienteNumero);
            return crearSocio.apply(codigo);
        });
    }

    private boolean obtenerBloqueo(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SQL_OBTENER_BLOQUEO)) {
            statement.setString(1, NOMBRE_BLOQUEO);
            statement.setInt(2, TIEMPO_ESPERA_SEGUNDOS);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) == 1 && !result.wasNull();
            }
        }
    }

    private void liberarBloqueo(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(SQL_LIBERAR_BLOQUEO)) {
            statement.setString(1, NOMBRE_BLOQUEO);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1 || result.wasNull()) {
                    throw new SQLException("MySQL no confirmó la liberación del bloqueo.");
                }
            }
        } catch (SQLException | RuntimeException errorLiberacion) {
            log.error("Falló RELEASE_LOCK para el bloqueo de socios; se expulsará "
                    + "la conexión del pool. lock={}", NOMBRE_BLOQUEO, errorLiberacion);
            boolean expulsada = invalidarConexion(connection, errorLiberacion);
            throw new IllegalStateException(expulsada
                    ? "No se pudo liberar el bloqueo; la conexión fue expulsada del pool."
                    : "No se pudo liberar el bloqueo ni confirmar la expulsión de la conexión.",
                    errorLiberacion);
        }
    }

    private boolean invalidarConexion(Connection connection, Throwable causa) {
        try {
            hikariDataSource.evictConnection(connection);
            return true;
        } catch (RuntimeException errorExpulsion) {
            causa.addSuppressed(errorExpulsion);
            log.error("Hikari no pudo expulsar la conexión con el bloqueo posiblemente "
                    + "retenido; se abortará la sesión. lock={}",
                    NOMBRE_BLOQUEO, errorExpulsion);
            try {
                connection.abort(Runnable::run);
            } catch (SQLException | RuntimeException errorAbort) {
                causa.addSuppressed(errorAbort);
                log.error("También falló el abortado de la conexión del bloqueo. lock={}",
                        NOMBRE_BLOQUEO, errorAbort);
            }
            return false;
        }
    }
}
