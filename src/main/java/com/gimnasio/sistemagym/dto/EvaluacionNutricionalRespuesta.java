package com.gimnasio.sistemagym.dto;

import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;

public record EvaluacionNutricionalRespuesta(
        Long idEvaluacion,
        Long idSocio,
        Long idNutricionista,
        Instant fechaEvaluacion,
        BigDecimal pesoKg,
        BigDecimal tallaM,
        BigDecimal porcentajeGrasa,
        BigDecimal masaMuscularKg,
        String habitosAlimenticios,
        String objetivosNutricionales,
        String recomendacionesNutricionales,
        String observacionesSeguimiento) {

    public static EvaluacionNutricionalRespuesta desde(EvaluacionNutricional evaluacion) {
        return new EvaluacionNutricionalRespuesta(
                evaluacion.getIdEvaluacion(),
                evaluacion.getSocio().getIdSocio(),
                evaluacion.getNutricionista().getIdUsuario(),
                evaluacion.getFechaEvaluacion().toInstant(ZoneOffset.UTC),
                evaluacion.getPesoKg(),
                evaluacion.getTallaM(),
                evaluacion.getPorcentajeGrasa(),
                evaluacion.getMasaMuscularKg(),
                evaluacion.getHabitosAlimenticios(),
                evaluacion.getObjetivosNutricionales(),
                evaluacion.getRecomendacionesNutricionales(),
                evaluacion.getObservacionesSeguimiento());
    }
}
