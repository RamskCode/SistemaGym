package com.gimnasio.sistemagym.dto;

import java.time.Instant;
import java.util.List;

public record ResumenNutricionRespuesta(
        long sociosDisponibles,
        long evaluacionesEsteMes,
        long sociosEvaluadosEsteMes,
        long evaluacionesUltimosSieteDias,
        List<EvaluacionReciente> evaluacionesRecientes) {

    public record EvaluacionReciente(
            Long idEvaluacion,
            Long idSocio,
            String codigoSocio,
            String nombres,
            String apellidos,
            Instant fechaEvaluacion) {
    }
}
