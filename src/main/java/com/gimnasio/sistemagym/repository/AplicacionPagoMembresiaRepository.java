package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.gimnasio.sistemagym.entity.AplicacionPagoMembresia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;

public interface AplicacionPagoMembresiaRepository
        extends JpaRepository<AplicacionPagoMembresia, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AplicacionPagoMembresia a where a.pago.idPago = :idPago")
    List<AplicacionPagoMembresia> findLockedByPagoId(
            @Param("idPago") Long idPago);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AplicacionPagoMembresia a "
            + "where a.membresia.idMembresia = :idMembresia")
    List<AplicacionPagoMembresia> findLockedByMembresiaId(
            @Param("idMembresia") Long idMembresia);

    @Query("""
            SELECT COALESCE(SUM(a.montoAplicado), 0)
            FROM AplicacionPagoMembresia a
            WHERE a.pago.idPago = :idPago
            """)
    BigDecimal sumarAplicacionesPorPago(
            @Param("idPago") Long idPago);

    @Query("""
        SELECT COALESCE(SUM(a.montoAplicado), 0)
        FROM AplicacionPagoMembresia a
        WHERE a.membresia.idMembresia = :idMembresia
        """)
    BigDecimal sumarAplicacionesPorMembresia(
            @Param("idMembresia") Long idMembresia);

    @Query("""
    SELECT COALESCE(SUM(a.montoAplicado), 0)
    FROM AplicacionPagoMembresia a
    WHERE a.membresia.idMembresia = :idMembresia
      AND a.tipoAplicacion = :tipoAplicacion
    """)
    BigDecimal sumarAplicacionesPorMembresiaYTipo(
            @Param("idMembresia") Long idMembresia,
            @Param("tipoAplicacion") TipoAplicacionPago tipoAplicacion);
}
