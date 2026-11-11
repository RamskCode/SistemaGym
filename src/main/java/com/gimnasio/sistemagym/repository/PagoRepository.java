package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Pago;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("select p from Pago p join fetch p.usuario where upper(p.estado) = 'CONFIRMADO' "
            + "and p.fechaPago >= :desde and p.fechaPago < :hasta order by p.fechaPago desc")
    Page<Pago> reportConfirmedBetween(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, Pageable pageable);

    long countByEstadoIgnoreCaseAndFechaPagoGreaterThanEqualAndFechaPagoLessThan(
            String estado, LocalDateTime desde, LocalDateTime hasta);

    @Query("select year(p.fechaPago), month(p.fechaPago), count(p), sum(p.monto) from Pago p "
            + "where upper(p.estado) = 'CONFIRMADO' and p.fechaPago >= :desde and p.fechaPago < :hasta "
            + "group by year(p.fechaPago), month(p.fechaPago) order by year(p.fechaPago), month(p.fechaPago)")
    java.util.List<Object[]> reportConfirmedTimeline(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.idPago = :id")
    Optional<Pago> findLockedById(@Param("id") Long id);

    @Query("select coalesce(sum(p.monto), 0) from Pago p "
            + "where upper(p.estado) = 'CONFIRMADO' "
            + "and p.fechaPago >= :desde and p.fechaPago < :hasta")
    BigDecimal sumConfirmedBetween(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
