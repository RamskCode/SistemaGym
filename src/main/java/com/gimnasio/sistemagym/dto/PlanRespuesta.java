package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.Plan;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PlanRespuesta(
        Long idPlan,
        String nombre,
        String descripcion,
        BigDecimal precio,
        Integer duracionDias,
        String estado,
        LocalDateTime fechaCreacion) {

    public static PlanRespuesta desde(Plan plan) {
        return new PlanRespuesta(
                plan.getIdPlan(),
                plan.getNombre(),
                plan.getDescripcion(),
                plan.getPrecio(),
                plan.getDuracionDias(),
                plan.getEstado(),
                plan.getFechaCreacion()
        );
    }
}
