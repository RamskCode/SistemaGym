package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.HistorialReporteIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistorialReporteIncidenciaRepository
        extends JpaRepository<HistorialReporteIncidencia, Long> {

    List<HistorialReporteIncidencia> findByReporte_IdReporteOrderByFechaHoraAscIdHistorialAsc(
            Long idReporte);

    List<HistorialReporteIncidencia> findByReporte_IdReporteInOrderByFechaHoraAscIdHistorialAsc(
            List<Long> reporteIds);
}
