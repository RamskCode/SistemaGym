package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.ReporteIncidencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ReporteIncidenciaRepository extends JpaRepository<ReporteIncidencia, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReporteIncidencia r where r.idReporte = :id")
    Optional<ReporteIncidencia> findLockedById(@Param("id") Long id);

    List<ReporteIncidencia> findByEntrenador_IdUsuarioOrderByFechaReporteDescIdReporteDesc(
            Long idEntrenador);

    List<ReporteIncidencia> findAllByOrderByFechaReporteDescIdReporteDesc();

    List<ReporteIncidencia> findByDetalleChecklist_IdDetalleIn(List<Long> detalleIds);

    Optional<ReporteIncidencia> findByIdReporteAndEntrenador_IdUsuario(
            Long idReporte, Long idEntrenador);
}
