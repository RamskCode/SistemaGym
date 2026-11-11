package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.Membresia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembresiaRespuesta(
        Long idMembresia,
        SocioRespuesta socio,
        PlanRespuesta plan,
        BigDecimal precioAcordado,
        LocalDate fechaInicio,
        LocalDate fechaVencimiento,
        LocalDate fechaLimitePago,
        String estado,
        String motivoCancelacion,
        LocalDateTime fechaCreacion) {

    public static MembresiaRespuesta desde(Membresia membresia) {
        return new MembresiaRespuesta(
                membresia.getIdMembresia(),
                membresia.getSocio() == null
                        ? null
                        : SocioRespuesta.desde(membresia.getSocio()),
                membresia.getPlan() == null
                        ? null
                        : PlanRespuesta.desde(membresia.getPlan()),
                membresia.getPrecioAcordado(),
                membresia.getFechaInicio(),
                membresia.getFechaVencimiento(),
                membresia.getFechaLimitePago(),
                membresia.getEstado(),
                membresia.getMotivoCancelacion(),
                membresia.getFechaCreacion()
        );
    }
}
