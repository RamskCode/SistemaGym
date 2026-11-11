package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;

import java.time.LocalDateTime;

/** Minimal projection used to aggregate a bounded employee attendance report. */
public interface AsistenciaLaboralReporteConsulta {
    EstadoAsistenciaLaboral getEstado();
    LocalDateTime getFechaEntradaUtc();
    LocalDateTime getFechaSalidaUtc();
}
