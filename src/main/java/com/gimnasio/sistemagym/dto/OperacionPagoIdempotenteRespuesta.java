package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.EstadoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.OperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;

import java.time.LocalDateTime;

/** Safe status contract; it intentionally excludes request details and the key. */
public record OperacionPagoIdempotenteRespuesta(
        String idOperacion,
        TipoOperacionPagoIdempotente tipoOperacion,
        EstadoOperacionPagoIdempotente estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        Long idMembresia,
        Long idPago,
        Long idAplicacion,
        String rechazoTipo,
        String rechazoMensaje,
        Object resultado) {

    public static OperacionPagoIdempotenteRespuesta desde(
            OperacionPagoIdempotente operacion) {
        return new OperacionPagoIdempotenteRespuesta(
                operacion.getIdOperacion(), operacion.getTipoOperacion(),
                operacion.getEstado(), operacion.getFechaCreacion(),
                operacion.getFechaActualizacion(), operacion.getIdMembresiaResultado(),
                operacion.getIdPagoResultado(), operacion.getIdAplicacionResultado(),
                operacion.getRechazoTipo(), operacion.getRechazoMensaje(), null);
    }
}
