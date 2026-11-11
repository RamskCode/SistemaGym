package com.gimnasio.sistemagym.integration.mysql;

import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.service.OperacionPagoIdempotenteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;

import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class OperacionPagoIdempotenteMySql84IT extends MySql84IntegrationTestSupport {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private OperacionPagoIdempotenteService service;

    private String correo;
    private Long idUsuario;

    @BeforeEach
    void crearActorDePrueba() {
        String rol = "IDEMPOTENCIA_TEST";
        jdbc.update("INSERT IGNORE INTO roles (nombre, descripcion) VALUES (?, ?)",
                rol, "Rol aislado para pruebas de idempotencia");
        Long idRol = jdbc.queryForObject("SELECT id_rol FROM roles WHERE nombre = ?", Long.class, rol);
        correo = "idem-" + UUID.randomUUID() + "@example.invalid";
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, contrasena_hash, estado) "
                        + "VALUES (?, 'Prueba', 'Idempotencia', ?, 'hash-no-utilizado', 'ACTIVO')",
                idRol, correo);
        idUsuario = jdbc.queryForObject("SELECT id_usuario FROM usuarios WHERE correo = ?", Long.class, correo);
    }

    @Test
    void mismaClaveConcurrenteCreaUnaSolaReservaYAmbasLecturasDevuelvenElMismoId() throws Exception {
        UUID clave = UUID.randomUUID();
        CyclicBarrier barrera = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var primera = pool.submit(() -> reservarTrasBarrera(barrera, clave));
            var segunda = pool.submit(() -> reservarTrasBarrera(barrera, clave));
            var r1 = primera.get(20, TimeUnit.SECONDS);
            var r2 = segunda.get(20, TimeUnit.SECONDS);
            assertEquals(r1.idOperacion(), r2.idOperacion());
            assertEquals(1L, contar(clave));
            assertEquals("PENDING", jdbc.queryForObject(
                    "SELECT estado FROM operaciones_pago_idempotentes WHERE id_operacion = ?",
                    String.class, r1.idOperacion()));
        }
    }

    @Test
    void unicidadPorUsuarioYClaveYSeparacionEntreUsuarios() {
        UUID clave = UUID.randomUUID();
        var propia = service.reservar(correo, clave,
                TipoOperacionPagoIdempotente.PAGO_POSTERIOR, "solicitud-A");
        String otroCorreo = crearOtroActor();
        var ajena = service.reservar(otroCorreo, clave,
                TipoOperacionPagoIdempotente.PAGO_POSTERIOR, "solicitud-A");

        assertNotEquals(propia.idOperacion(), ajena.idOperacion());
        assertEquals(propia.idOperacion(), service.consultar(correo, propia.idOperacion()).idOperacion());
        assertThrows(RuntimeException.class, () -> service.consultar(correo, ajena.idOperacion()));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes WHERE clave_idempotencia = ?",
                Long.class, clave.toString()));
    }

    @Test
    void restriccionesRechazanClaveDuplicadaYConfirmacionSinReferenciasDeNegocio() {
        UUID clave = UUID.randomUUID();
        var creada = service.reservar(correo, clave,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "solicitud-B");
        String hash = jdbc.queryForObject(
                "SELECT hash_solicitud FROM operaciones_pago_idempotentes WHERE id_operacion = ?",
                String.class, creada.idOperacion());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO operaciones_pago_idempotentes "
                        + "(id_operacion, id_usuario, clave_idempotencia, tipo_operacion, hash_solicitud, estado) "
                        + "VALUES (?, ?, ?, ?, ?, 'PENDING')",
                UUID.randomUUID().toString(), idUsuario, clave.toString(),
                "MEMBRESIA_INICIAL", hash));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE operaciones_pago_idempotentes SET estado = 'CONFIRMED' WHERE id_operacion = ?",
                creada.idOperacion()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE operaciones_pago_idempotentes SET rechazo_tipo = 'ARGUMENTO' "
                        + "WHERE id_operacion = ?",
                creada.idOperacion()),
                "PENDING no debe admitir datos de rechazo");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE operaciones_pago_idempotentes SET estado = 'REJECTED' "
                        + "WHERE id_operacion = ?",
                creada.idOperacion()),
                "REJECTED debe incluir tipo y mensaje de rechazo");
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT estado FROM operaciones_pago_idempotentes WHERE id_operacion = ?",
                String.class, creada.idOperacion()));
    }

    @Test
    void recuperacionDeLaMismaClaveConservaPendienteSinInventarConfirmacion() {
        UUID clave = UUID.randomUUID();
        var original = service.reservar(correo, clave,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "solicitud-C");
        var recuperada = service.reservar(correo, clave,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "solicitud-C");

        assertEquals(original.idOperacion(), recuperada.idOperacion());
        assertEquals("PENDING", recuperada.estado().name());
        assertNull(recuperada.idPago());
        assertEquals(1L, contar(clave));
    }

    @Test
    void consultaPorClaveEsPropiaYDevuelvePendienteSinCrearPagos() {
        UUID clave = UUID.randomUUID();
        var reserva = service.reservar(correo, clave,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "consulta-pendiente");
        long pagosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM pagos", Long.class);

        var encontrada = service.consultarPorClave(correo, clave.toString());
        assertEquals(reserva.idOperacion(), encontrada.idOperacion());
        assertEquals("PENDING", encontrada.estado().name());
        assertNull(encontrada.resultado());
        assertEquals(pagosAntes, jdbc.queryForObject("SELECT COUNT(*) FROM pagos", Long.class));
        assertThrows(RuntimeException.class,
                () -> service.consultarPorClave(crearOtroActor(), clave.toString()));
        assertThrows(RuntimeException.class,
                () -> service.consultarPorClave(correo, UUID.randomUUID().toString()));
        assertThrows(IllegalArgumentException.class,
                () -> service.consultarPorClave(correo, "not-a-uuid"));
        assertEquals(1L, contar(clave));
    }

    private com.gimnasio.sistemagym.dto.OperacionPagoIdempotenteRespuesta reservarTrasBarrera(
            CyclicBarrier barrier, UUID key) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return service.reservar(correo, key,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, "concurrente");
    }

    private String crearOtroActor() {
        String otro = "idem-" + UUID.randomUUID() + "@example.invalid";
        Long role = jdbc.queryForObject("SELECT id_rol FROM roles WHERE nombre = ?", Long.class,
                "IDEMPOTENCIA_TEST");
        jdbc.update("INSERT INTO usuarios (id_rol, nombres, apellidos, correo, contrasena_hash, estado) "
                        + "VALUES (?, 'Otra', 'Prueba', ?, 'hash-no-utilizado', 'ACTIVO')", role, otro);
        return otro;
    }

    private long contar(UUID key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM operaciones_pago_idempotentes WHERE id_usuario = ? "
                        + "AND clave_idempotencia = ?",
                Long.class, idUsuario, key.toString());
    }
}
