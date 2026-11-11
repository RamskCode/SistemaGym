package com.gimnasio.sistemagym.repository;

import java.time.LocalDate;
import java.util.List;
import com.gimnasio.sistemagym.entity.Membresia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MembresiaRepository extends JpaRepository<Membresia, Long> {
    @Query("select m from Membresia m join fetch m.socio join fetch m.plan "
            + "where m.fechaCreacion >= :desde and m.fechaCreacion < :hasta "
            + "and (:planId is null or m.plan.idPlan = :planId) order by m.fechaCreacion desc")
    Page<Membresia> reportBetween(@Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta, @Param("planId") Long planId,
            Pageable pageable);

    @Query("select m.plan.idPlan, m.plan.nombre, count(m) from Membresia m "
            + "where m.fechaCreacion >= :desde and m.fechaCreacion < :hasta "
            + "group by m.plan.idPlan, m.plan.nombre order by count(m) desc")
    List<Object[]> reportPlans(@Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta);

    @Query("select m.plan.idPlan, m.plan.nombre, count(m) from Membresia m "
            + "where m.fechaCreacion >= :desde and m.fechaCreacion < :hasta "
            + "and (:planId is null or m.plan.idPlan = :planId) group by m.plan.idPlan, m.plan.nombre order by count(m) desc")
    List<Object[]> reportPlansFiltered(@Param("desde") java.time.LocalDateTime desde,
            @Param("hasta") java.time.LocalDateTime hasta, @Param("planId") Long planId);

    @Query("select count(m) from Membresia m where upper(m.estado) = :estado "
            + "and m.fechaVencimiento >= :desde and m.fechaVencimiento < :hasta")
    long reportCountStateByExpiry(@Param("estado") String estado,
            @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    @Query("select count(m) from Membresia m where upper(m.estado) = 'ACTIVA' "
            + "and m.fechaVencimiento >= :desde and m.fechaVencimiento <= :hasta")
    long reportUpcoming(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
    long countByEstadoIgnoreCase(String estado);
    List<Membresia> findBySocio_IdSocio(Long idSocio);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Membresia m where m.idMembresia = :id")
    Optional<Membresia> findLockedById(@Param("id") Long id);

    List<Membresia> findByEstadoAndFechaLimitePagoBefore(
            String estado,
            LocalDate fecha
    );

    @Query("""
            select m.idMembresia
            from Membresia m
            where m.estado = :estado
              and m.fechaLimitePago < :fecha
            """)
    List<Long> findIdsByEstadoAndFechaLimitePagoBefore(
            @Param("estado") String estado,
            @Param("fecha") LocalDate fecha);

    @Query("""
            select m.idMembresia
            from Membresia m
            where m.estado = :estado
              and m.fechaVencimiento < :fecha
            """)
    List<Long> findIdsByEstadoAndFechaVencimientoBefore(
            @Param("estado") String estado,
            @Param("fecha") LocalDate fecha);
}
