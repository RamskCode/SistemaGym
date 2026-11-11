package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.SocioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GeneradorCodigoSocioMySqlTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private SocioRepository socioRepository;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement obtenerBloqueo;

    @Mock
    private PreparedStatement liberarBloqueo;

    @Mock
    private ResultSet resultadoBloqueo;

    @Mock
    private ResultSet resultadoLiberacion;

    private GeneradorCodigoSocioMySql generador;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        generador = new GeneradorCodigoSocioMySql(
                jdbcTemplate, socioRepository, hikariDataSource);

        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenAnswer(invocation -> {
            ConnectionCallback<?> callback = invocation.getArgument(0);
            return callback.doInConnection(connection);
        });
        when(connection.prepareStatement(contains("GET_LOCK")))
                .thenReturn(obtenerBloqueo);
        lenient().when(connection.prepareStatement(contains("RELEASE_LOCK")))
                .thenReturn(liberarBloqueo);
        when(obtenerBloqueo.executeQuery()).thenReturn(resultadoBloqueo);
        lenient().when(resultadoBloqueo.next()).thenReturn(true);
        lenient().when(resultadoBloqueo.getInt(1)).thenReturn(1);
        lenient().when(liberarBloqueo.executeQuery()).thenReturn(resultadoLiberacion);
        lenient().when(resultadoLiberacion.next()).thenReturn(true);
        lenient().when(resultadoLiberacion.getInt(1)).thenReturn(1);
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void generaElPrimerCodigoYConservaElBloqueoHastaDespuesDelCommit()
            throws Exception {
        when(socioRepository.obtenerUltimoNumeroCodigo()).thenReturn(null);
        AtomicReference<String> codigo = new AtomicReference<>();
        Socio socio = new Socio();

        Socio resultado = generador.crearConSiguienteCodigo(generado -> {
            codigo.set(generado);
            return socio;
        });

        assertSame(socio, resultado);
        assertEquals("SOC-0001", codigo.get());
        verify(connection, never()).prepareStatement(contains("RELEASE_LOCK"));

        completarTransaccion(TransactionSynchronization.STATUS_COMMITTED);

        verify(connection).prepareStatement(contains("RELEASE_LOCK"));
    }

    @Test
    void continuaDesdeElMayorCodigoYNoReutilizaUnHuecoInferior() {
        when(socioRepository.obtenerUltimoNumeroCodigo()).thenReturn(7);
        AtomicReference<String> codigo = new AtomicReference<>();

        generador.crearConSiguienteCodigo(generado -> {
            codigo.set(generado);
            return new Socio();
        });

        assertEquals("SOC-0008", codigo.get());
        completarTransaccion(TransactionSynchronization.STATUS_COMMITTED);
    }

    @Test
    void tiempoDeEsperaAgotadoNoEjecutaLaCreacion() throws Exception {
        when(resultadoBloqueo.getInt(1)).thenReturn(0);
        AtomicReference<Boolean> callbackEjecutado = new AtomicReference<>(false);

        assertThrows(ConflictoException.class,
                () -> generador.crearConSiguienteCodigo(generado -> {
                    callbackEjecutado.set(true);
                    return new Socio();
                }));

        assertFalse(callbackEjecutado.get());
        verifyNoInteractions(socioRepository);
        verify(connection, never()).prepareStatement(contains("RELEASE_LOCK"));
    }

    @Test
    void liberaElBloqueoDespuesDeRollbackSiGuardarFalla() throws Exception {
        when(socioRepository.obtenerUltimoNumeroCodigo()).thenReturn(3);

        assertThrows(IllegalStateException.class,
                () -> generador.crearConSiguienteCodigo(generado -> {
                    throw new IllegalStateException("fallo simulado");
                }));

        verify(connection, never()).prepareStatement(contains("RELEASE_LOCK"));
        completarTransaccion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(connection).prepareStatement(contains("RELEASE_LOCK"));
    }

    @Test
    void errorGetLockIntentaLiberarEnLaMismaSesionYConservaLaExcepcion()
            throws Exception {
        SQLException original = new SQLException("error de lectura simulado");
        when(obtenerBloqueo.executeQuery()).thenThrow(original);

        SQLException recibida = assertThrows(SQLException.class,
                () -> generador.crearConSiguienteCodigo(codigo -> new Socio()));

        assertSame(original, recibida);
        verify(connection).prepareStatement(contains("RELEASE_LOCK"));
        verify(connection, never()).abort(any());
        verifyNoInteractions(socioRepository);
    }

    @Test
    void adquisicionAmbiguaYFalloDeLiberacionExpulsanLaConexion()
            throws Exception {
        SQLException original = new SQLException("GET_LOCK ambiguo");
        when(obtenerBloqueo.executeQuery()).thenThrow(original);
        when(resultadoLiberacion.next()).thenReturn(false);

        SQLException recibida = assertThrows(SQLException.class,
                () -> generador.crearConSiguienteCodigo(codigo -> new Socio()));

        assertSame(original, recibida);
        assertEquals(1, recibida.getSuppressed().length);
        verify(hikariDataSource).evictConnection(connection);
        verifyNoInteractions(socioRepository);
    }

    @Test
    void errorReleaseLockExpulsaConexionDespuesDeFinalizarTransaccion()
            throws Exception {
        when(socioRepository.obtenerUltimoNumeroCodigo()).thenReturn(null);
        when(resultadoLiberacion.next()).thenReturn(false);
        generador.crearConSiguienteCodigo(codigo -> new Socio());

        assertThrows(IllegalStateException.class,
                () -> completarTransaccion(TransactionSynchronization.STATUS_COMMITTED));

        verify(hikariDataSource).evictConnection(connection);
    }

    @Test
    void siFallaLaExpulsionIndividualSeIntentaAbortarLaSesion()
            throws Exception {
        when(socioRepository.obtenerUltimoNumeroCodigo()).thenReturn(null);
        when(resultadoLiberacion.next()).thenReturn(false);
        doThrow(new IllegalStateException("fallo Hikari simulado"))
                .when(hikariDataSource).evictConnection(connection);
        generador.crearConSiguienteCodigo(codigo -> new Socio());

        assertThrows(IllegalStateException.class,
                () -> completarTransaccion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(connection).abort(any());
    }

    private void completarTransaccion(int estado) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sincronizacion -> sincronizacion.afterCompletion(estado));
    }
}
