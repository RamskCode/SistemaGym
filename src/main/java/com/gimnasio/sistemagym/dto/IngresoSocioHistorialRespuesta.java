package com.gimnasio.sistemagym.dto;

import java.time.LocalDateTime;

public record IngresoSocioHistorialRespuesta(
        Long idIngreso,
        LocalDateTime fechaHora,
        String modalidad) { }
