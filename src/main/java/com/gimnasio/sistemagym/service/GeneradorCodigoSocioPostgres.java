package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.exception.ConflictoException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;
import java.util.function.Function;
import java.sql.PreparedStatement;

/** PostgreSQL implementation; transaction advisory lock spans code read and insert. */
@Component
@ConditionalOnProperty(name = "app.database.provider", havingValue = "postgresql")
public class GeneradorCodigoSocioPostgres implements GeneradorCodigoSocio {

    private static final int LOCK_NAMESPACE = 1_936_728_421;
    private static final int LOCK_KEY = 1;
    private static final String SQL_ULTIMO_CODIGO = """
            SELECT MAX(SUBSTRING(codigo_socio FROM 5)::BIGINT)
            FROM socios
            WHERE codigo_socio COLLATE "C" ~ '^SOC-[0-9]+$'
            """;

    private final JdbcTemplate jdbcTemplate;

    public GeneradorCodigoSocioPostgres(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Socio crearConSiguienteCodigo(Function<String, Socio> crearSocio) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La generación del código requiere una transacción activa.");
        }

        // A transaction-scoped advisory lock is released by PostgreSQL on both
        // commit and rollback. It uses the same transaction/connection as JPA.
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, LOCK_NAMESPACE);
                statement.setInt(2, LOCK_KEY);
                statement.execute();
            }
            return null;
        });
        Long ultimoNumero = jdbcTemplate.queryForObject(SQL_ULTIMO_CODIGO, Long.class);
        if (ultimoNumero != null && ultimoNumero >= Integer.MAX_VALUE) {
            throw new ConflictoException("Se agotó el rango disponible para códigos de socio.");
        }
        int siguienteNumero = ultimoNumero == null ? 1 : ultimoNumero.intValue() + 1;
        return crearSocio.apply(String.format(Locale.ROOT, "SOC-%04d", siguienteNumero));
    }
}
