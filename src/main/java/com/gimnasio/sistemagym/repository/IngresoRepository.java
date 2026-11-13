package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.TipoIngreso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngresoRepository extends JpaRepository<Ingreso, Long> {

    @Query("select i from Ingreso i left join fetch i.socio left join fetch i.contacto "
            + "left join fetch i.pago left join fetch i.paseOrigen po left join fetch po.pago "
            + "where i.idIngreso = :id")
    Optional<Ingreso> findForAdministrativeDetail(@Param("id") Long id);

    @Query("select i from Ingreso i left join fetch i.socio left join fetch i.contacto "
            + "left join fetch i.usuario left join fetch i.paseOrigen "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta order by i.fechaHora desc")
    List<Ingreso> reportBetween(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("select i from Ingreso i left join fetch i.socio left join fetch i.contacto "
            + "left join fetch i.usuario left join fetch i.paseOrigen "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta order by i.fechaHora desc")
    List<Ingreso> reportPage(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, org.springframework.data.domain.Pageable pageable);

    @Query(value = "select i from Ingreso i left join fetch i.socio s left join fetch i.contacto c "
            + "left join fetch i.usuario u left join fetch i.paseOrigen po left join fetch i.pago p "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta "
            + "and (:buscar = '' or lower(concat(coalesce(s.nombres, ''), ' ', coalesce(s.apellidos, ''), ' ', "
            + "coalesce(c.nombre, ''), ' ', i.numeroDocumento)) like concat('%', :buscar, '%')) "
            + "and (:modalidad = '' or (:modalidad = 'MEMBRESIA' and i.tipoIngreso = "
            + "com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO) or (:modalidad = 'PASE_DIARIO' and "
            + "i.tipoIngreso = com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE and "
            + "(i.pago is not null or i.paseOrigen is not null))) "
            + "and (:responsableId is null or u.idUsuario = :responsableId) "
            + "order by i.fechaHora desc, i.idIngreso desc",
            countQuery = "select count(i) from Ingreso i left join i.socio s left join i.contacto c "
                    + "where i.fechaHora >= :desde and i.fechaHora < :hasta "
                    + "and (:buscar = '' or lower(concat(coalesce(s.nombres, ''), ' ', coalesce(s.apellidos, ''), ' ', "
                    + "coalesce(c.nombre, ''), ' ', i.numeroDocumento)) like concat('%', :buscar, '%')) "
                    + "and (:modalidad = '' or (:modalidad = 'MEMBRESIA' and i.tipoIngreso = "
                    + "com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO) or (:modalidad = 'PASE_DIARIO' and "
                    + "i.tipoIngreso = com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE and "
                    + "(i.pago is not null or i.paseOrigen is not null))) "
                    + "and (:responsableId is null or i.usuario.idUsuario = :responsableId)")
    Page<Ingreso> reportPageFiltered(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, @Param("buscar") String buscar,
            @Param("modalidad") String modalidad, @Param("responsableId") Long responsableId,
            Pageable pageable);

    @Query("select year(i.fechaHora), month(i.fechaHora), i.tipoIngreso, count(i) from Ingreso i "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta "
            + "group by year(i.fechaHora), month(i.fechaHora), i.tipoIngreso order by year(i.fechaHora), month(i.fechaHora)")
    List<Object[]> reportTimeline(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("select count(i) from Ingreso i where i.tipoIngreso = com.gimnasio.sistemagym.entity.TipoIngreso.SOCIO "
            + "and i.fechaHora >= :desde and i.fechaHora < :hasta")
    long reportSocioCount(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("select count(i) from Ingreso i where i.tipoIngreso = com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE "
            + "and i.fechaHora >= :desde and i.fechaHora < :hasta")
    long reportVisitanteCount(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("select i.tipoIngreso, i.tipoDocumento, i.numeroDocumento from Ingreso i "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta "
            + "group by i.tipoIngreso, i.tipoDocumento, i.numeroDocumento")
    List<Object[]> reportDistinctPeople(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    @Query("select distinct u.idUsuario, u.nombres, u.apellidos from Ingreso i join i.usuario u "
            + "where i.fechaHora >= :desde and i.fechaHora < :hasta order by u.apellidos, u.nombres")
    List<Object[]> reportRegistrars(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    long countByFechaHoraGreaterThanEqualAndFechaHoraLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    long countByTipoIngresoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
            TipoIngreso tipoIngreso, LocalDateTime desde, LocalDateTime hasta);

    @Query("select count(i) from Ingreso i where i.fechaHora >= :desde and i.fechaHora < :hasta")
    long countBetween(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("select i from Ingreso i left join fetch i.socio "
            + "left join fetch i.contacto order by i.fechaHora desc, i.idIngreso desc")
    List<Ingreso> findRecentDashboard(Pageable pageable);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
            TipoDocumento tipoDocumento,
            String numeroDocumento,
            LocalDateTime desde,
            LocalDateTime hasta);

    @Query("select i from Ingreso i left join fetch i.pago "
            + "where i.socio.idSocio = :socioId and i.tipoIngreso = "
            + "com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE "
            + "and i.pago is not null and i.fechaHora >= :desde and i.fechaHora < :hasta "
            + "order by i.fechaHora asc, i.idIngreso asc")
    Optional<Ingreso> buscarPaseDiarioVigente(@Param("socioId") Long socioId,
            @Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    Optional<Ingreso> findByClaveIdempotencia(String claveIdempotencia);

    @Query("select i from Ingreso i left join fetch i.pago "
            + "left join fetch i.paseOrigen po left join fetch po.pago "
            + "where i.socio.idSocio = :socioId "
            + "and i.tipoIngreso = com.gimnasio.sistemagym.entity.TipoIngreso.VISITANTE "
            + "order by i.fechaHora desc, i.idIngreso desc")
    List<Ingreso> listarPasesDiariosPorSocio(@Param("socioId") Long socioId);

    @Query(value = "select i from Ingreso i where i.socio.idSocio = :socioId "
            + "order by i.fechaHora desc, i.idIngreso desc",
            countQuery = "select count(i) from Ingreso i where i.socio.idSocio = :socioId")
    Page<Ingreso> listarHistorialSocio(@Param("socioId") Long socioId, Pageable pageable);
}
