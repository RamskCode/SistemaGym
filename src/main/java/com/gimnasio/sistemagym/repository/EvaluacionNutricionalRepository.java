package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.EvaluacionNutricional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluacionNutricionalRepository
        extends JpaRepository<EvaluacionNutricional, Long> {

    List<EvaluacionNutricional> findBySocio_IdSocioOrderByFechaEvaluacionDescIdEvaluacionDesc(
            Long idSocio);

    long countByFechaEvaluacionGreaterThanEqualAndFechaEvaluacionLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    @Query("select count(distinct e.socio.idSocio) from EvaluacionNutricional e "
            + "where e.fechaEvaluacion >= :desde and e.fechaEvaluacion < :hasta")
    long countDistinctSociosEvaluados(
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    List<EvaluacionNutricional> findTop8ByOrderByFechaEvaluacionDescIdEvaluacionDesc();
}
