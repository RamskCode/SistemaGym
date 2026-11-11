package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.PagoPosteriorMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarMembresiaRequest;
import com.gimnasio.sistemagym.dto.RegistrarPagoPosteriorRequest;
import com.gimnasio.sistemagym.dto.RegistroMembresiaRespuesta;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.exception.RechazoDeterministaOperacionException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Service
public class MembresiaIdempotenteService {
    private final OperacionPagoIdempotenteService operaciones;
    private final MembresiaService membresias;

    public MembresiaIdempotenteService(
            OperacionPagoIdempotenteService operaciones, MembresiaService membresias) {
        this.operaciones = operaciones;
        this.membresias = membresias;
    }

    public RegistroMembresiaRespuesta registrar(
            UUID key, RegistrarMembresiaRequest request, String correo) {
        RegistrarMembresiaRequest normalizada = new RegistrarMembresiaRequest();
        normalizada.setIdSocio(request.getIdSocio());
        normalizada.setIdPlan(request.getIdPlan());
        normalizada.setMontoPrimerPago(request.getMontoPrimerPago());
        normalizada.setMetodoPago(texto(request.getMetodoPago(), true));
        normalizada.setReferencia(texto(request.getReferencia(), false));
        normalizada.setObservaciones(texto(request.getObservaciones(), false));
        String canon = "MEMBRESIA_INICIAL|" + normalizada.getIdSocio() + "|" + normalizada.getIdPlan()
                + "|" + importe(normalizada.getMontoPrimerPago()) + "|" + normalizada.getMetodoPago()
                + "|" + canonTexto(normalizada.getReferencia()) + "|" + canonTexto(normalizada.getObservaciones());
        var reserva = operaciones.reservar(correo, key,
                TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, canon);
        String hash = operaciones.hashSolicitud(canon);
        try {
            return operaciones.procesar(correo, reserva.idOperacion(),
                    TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL, hash,
                    RegistroMembresiaRespuesta.class,
                    () -> membresias.registrar(normalizada, correo),
                    resultado -> new Long[]{resultado.idMembresia(), resultado.idPago(), resultado.idAplicacion()});
        } catch (RechazoDeterministaOperacionException ex) {
            operaciones.rechazar(correo, reserva.idOperacion(), "CONFLICTO", ex.getMessage());
            throw (IllegalStateException) ex.getCause();
        } catch (IllegalArgumentException | ConflictoException | RecursoNoEncontradoException ex) {
            operaciones.rechazar(correo, reserva.idOperacion(), tipoRechazo(ex), ex.getMessage());
            throw ex;
        }
    }

    public PagoPosteriorMembresiaRespuesta registrarPago(
            Long idMembresia, UUID key, RegistrarPagoPosteriorRequest request, String correo) {
        RegistrarPagoPosteriorRequest normalizada = new RegistrarPagoPosteriorRequest(
                request.monto(), texto(request.metodoPago(), true),
                texto(request.referencia(), false), texto(request.observaciones(), false));
        String canon = "PAGO_POSTERIOR|" + idMembresia + "|" + importe(normalizada.monto())
                + "|" + normalizada.metodoPago() + "|" + canonTexto(normalizada.referencia())
                + "|" + canonTexto(normalizada.observaciones());
        var reserva = operaciones.reservar(correo, key,
                TipoOperacionPagoIdempotente.PAGO_POSTERIOR, canon);
        String hash = operaciones.hashSolicitud(canon);
        try {
            return operaciones.procesar(correo, reserva.idOperacion(),
                    TipoOperacionPagoIdempotente.PAGO_POSTERIOR, hash,
                    PagoPosteriorMembresiaRespuesta.class,
                    () -> membresias.registrarPagoPosterior(idMembresia, normalizada, correo),
                    resultado -> new Long[]{resultado.idMembresia(), resultado.idPago(), resultado.idAplicacion()});
        } catch (RechazoDeterministaOperacionException ex) {
            operaciones.rechazar(correo, reserva.idOperacion(), "CONFLICTO", ex.getMessage());
            throw (IllegalStateException) ex.getCause();
        } catch (IllegalArgumentException | ConflictoException | RecursoNoEncontradoException ex) {
            operaciones.rechazar(correo, reserva.idOperacion(), tipoRechazo(ex), ex.getMessage());
            throw ex;
        }
    }

    private String tipoRechazo(RuntimeException ex) {
        if (ex instanceof ConflictoException) return "CONFLICTO";
        if (ex instanceof RecursoNoEncontradoException) return "NO_ENCONTRADO";
        return "ARGUMENTO";
    }

    private String importe(BigDecimal amount) {
        return amount == null ? "null" : amount.stripTrailingZeros().toPlainString();
    }

    private String texto(String value, boolean upper) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return upper ? normalized.toUpperCase(Locale.ROOT) : normalized;
    }

    private String canonTexto(String value) {
        return value == null ? "" : value;
    }
}
