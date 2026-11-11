package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.EstadoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.OperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoClaveIdempotenciaException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.OperacionPagoIdempotenteRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperacionPagoIdempotenteServiceTest {
    private static final String CORREO = "caja@example.test";
    private static final UUID CLAVE = UUID.fromString("e020cfbc-f2cd-4f6b-968f-338fbd5324a1");
    private static final String SOLICITUD = "{\"idSocio\":7,\"idPlan\":2,\"monto\":\"40.00\"}";

    @Mock private OperacionPagoIdempotenteRepository operacionRepository;
    @Mock private UsuarioRepository usuarioRepository;
    private OperacionPagoIdempotenteService service;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        service = new OperacionPagoIdempotenteService(operacionRepository, usuarioRepository);
        usuario = new Usuario();
        usuario.setCorreo(CORREO);
        ReflectionTestUtils.setField(usuario, "idUsuario", 17L);
        when(usuarioRepository.findByCorreo(CORREO)).thenReturn(Optional.of(usuario));
    }

    @Test
    void reservaInicialQuedaPendienteYSinReferenciasDeCobro() {
        when(operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(17L, CLAVE.toString()))
                .thenReturn(Optional.empty());
        when(operacionRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        var respuesta = service.reservar(CORREO, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, SOLICITUD);

        ArgumentCaptor<OperacionPagoIdempotente> captor =
                ArgumentCaptor.forClass(OperacionPagoIdempotente.class);
        verify(operacionRepository).saveAndFlush(captor.capture());
        var guardada = captor.getValue();
        assertEquals(EstadoOperacionPagoIdempotente.PENDING, guardada.getEstado());
        assertEquals(64, guardada.getHashSolicitud().length());
        assertEquals(EstadoOperacionPagoIdempotente.PENDING, respuesta.estado());
        assertNull(respuesta.idPago());
        assertNull(respuesta.idMembresia());
        assertNull(respuesta.idAplicacion());
    }

    @Test
    void repeticionIdenticaDevuelveLaMismaReservaPendiente() {
        var fila = OperacionPagoIdempotente.reservar(usuario, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, hash(SOLICITUD));
        when(operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(17L, CLAVE.toString()))
                .thenReturn(Optional.of(fila));

        var repetida = service.reservar(CORREO, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, SOLICITUD);

        assertEquals(fila.getIdOperacion(), repetida.idOperacion());
        assertEquals(EstadoOperacionPagoIdempotente.PENDING, repetida.estado());
        verify(operacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void claveConContenidoOTipoDiferenteProduceConflicto() {
        var fila = OperacionPagoIdempotente.reservar(usuario, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, hash(SOLICITUD));
        when(operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(17L, CLAVE.toString()))
                .thenReturn(Optional.of(fila));

        assertThrows(ConflictoClaveIdempotenciaException.class, () -> service.reservar(
                CORREO, CLAVE, TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL,
                SOLICITUD.replace("40.00", "50.00")));
        assertThrows(ConflictoClaveIdempotenciaException.class, () -> service.reservar(
                CORREO, CLAVE, TipoOperacionPagoIdempotente.PAGO_POSTERIOR, SOLICITUD));
        verify(operacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void colisionDeInsercionReleeLaReservaGanadora() {
        var fila = OperacionPagoIdempotente.reservar(usuario, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, hash(SOLICITUD));
        when(operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(17L, CLAVE.toString()))
                .thenReturn(Optional.empty(), Optional.of(fila));
        when(operacionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var respuesta = service.reservar(CORREO, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, SOLICITUD);

        assertEquals(fila.getIdOperacion(), respuesta.idOperacion());
        assertEquals(EstadoOperacionPagoIdempotente.PENDING, respuesta.estado());
    }

    @Test
    void consultaEsPropietarioLimitadaYNoExponeOperacionesAjenas() {
        when(operacionRepository.findByIdOperacionAndUsuario_IdUsuario("opaque-id", 17L))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.consultar(CORREO, "opaque-id"));
        verify(operacionRepository).findByIdOperacionAndUsuario_IdUsuario("opaque-id", 17L);
        verify(operacionRepository, never()).saveAndFlush(any());
    }

    @Test
    void hashEsFijoYLaConfirmacionQuedaEnMetodoTransaccionalDeEjecucion() {
        var fila = OperacionPagoIdempotente.reservar(usuario, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, hash(SOLICITUD));
        when(operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(17L, CLAVE.toString()))
                .thenReturn(Optional.of(fila));

        assertEquals(64, fila.getHashSolicitud().length());
        assertTrue(java.util.Arrays.stream(OperacionPagoIdempotenteService.class.getMethods())
                .filter(method -> method.getName().equals("procesar"))
                .anyMatch(method -> method.isAnnotationPresent(
                        org.springframework.transaction.annotation.Transactional.class)));
        assertEquals(EstadoOperacionPagoIdempotente.PENDING, service.reservar(CORREO, CLAVE,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, SOLICITUD).estado());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
