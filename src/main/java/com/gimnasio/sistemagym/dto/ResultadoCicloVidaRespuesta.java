package com.gimnasio.sistemagym.dto;

import java.time.LocalDate;

/** Resultado de ejecutar el proceso automático de ciclo de vida. */
public record ResultadoCicloVidaRespuesta(
        int canceladas,
        int vencidas,
        LocalDate fecha) {
}
