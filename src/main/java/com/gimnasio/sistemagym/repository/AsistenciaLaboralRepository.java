package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AsistenciaLaboralRepository extends JpaRepository<AsistenciaLaboral, Long> {

    @Query("select a.idAsistencia as idAsistencia, "
            + "empleado.idUsuario as idEmpleado, "
            + "empleado.nombres as nombresEmpleado, "
            + "empleado.apellidos as apellidosEmpleado, "
            + "empleado.correo as correoEmpleado, "
            + "a.fechaEntradaUtc as fechaEntradaUtc, "
            + "a.fechaSalidaUtc as fechaSalidaUtc, "
            + "a.fechaLaboral as fechaLaboral, "
            + "a.estado as estado, "
            + "entrada.idUsuario as idRegistradorEntrada, "
            + "entrada.nombres as nombresRegistradorEntrada, "
            + "entrada.apellidos as apellidosRegistradorEntrada, "
            + "entrada.correo as correoRegistradorEntrada, "
            + "salida.idUsuario as idRegistradorSalida, "
            + "salida.nombres as nombresRegistradorSalida, "
            + "salida.apellidos as apellidosRegistradorSalida, "
            + "salida.correo as correoRegistradorSalida "
            + "from AsistenciaLaboral a "
            + "join a.usuario empleado "
            + "join a.registradoEntrada entrada "
            + "left join a.registradoSalida salida "
            + "where a.idAsistencia = :idAsistencia")
    Optional<AsistenciaLaboralDetalleConsulta> consultarDetalleHistorial(
            @Param("idAsistencia") Long idAsistencia);

    @Query("select a.estado as estado, "
            + "a.fechaEntradaUtc as fechaEntradaUtc, "
            + "a.fechaSalidaUtc as fechaSalidaUtc "
            + "from AsistenciaLaboral a "
            + "where a.usuario.idUsuario = :idEmpleado "
            + "and a.fechaLaboral between :fechaDesde and :fechaHasta "
            + "order by a.fechaLaboral asc, a.fechaEntradaUtc asc, a.idAsistencia asc")
    List<AsistenciaLaboralReporteConsulta> consultarDatosReporteEmpleado(
            @Param("idEmpleado") Long idEmpleado,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta);

    @Query("select a.usuario.idUsuario from AsistenciaLaboral a "
            + "where a.idAsistencia = :idAsistencia")
    Optional<Long> findIdUsuarioByIdAsistencia(@Param("idAsistencia") Long idAsistencia);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsistenciaLaboral a where a.idAsistencia = :idAsistencia")
    Optional<AsistenciaLaboral> findLockedByIdAsistencia(
            @Param("idAsistencia") Long idAsistencia);

    @Query(value = "select a.idAsistencia as idAsistencia, "
            + "a.usuario.idUsuario as idEmpleado, "
            + "a.fechaEntradaUtc as fechaEntradaUtc, "
            + "a.fechaSalidaUtc as fechaSalidaUtc, "
            + "a.fechaLaboral as fechaLaboral, "
            + "a.estado as estado "
            + "from AsistenciaLaboral a "
            + "where (:idEmpleado is null or a.usuario.idUsuario = :idEmpleado) "
            + "and (:fechaDesde is null or a.fechaLaboral >= :fechaDesde) "
            + "and (:fechaHasta is null or a.fechaLaboral <= :fechaHasta) "
            + "and (:estado is null or a.estado = :estado)",
            countQuery = "select count(a) from AsistenciaLaboral a "
                    + "where (:idEmpleado is null or a.usuario.idUsuario = :idEmpleado) "
                    + "and (:fechaDesde is null or a.fechaLaboral >= :fechaDesde) "
                    + "and (:fechaHasta is null or a.fechaLaboral <= :fechaHasta) "
                    + "and (:estado is null or a.estado = :estado)")
    Page<AsistenciaLaboralConsulta> consultarHistorial(
            @Param("idEmpleado") Long idEmpleado,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta,
            @Param("estado") EstadoAsistenciaLaboral estado,
            Pageable pageable);

    List<AsistenciaLaboral> findByUsuario_IdUsuarioAndEstadoOrderByFechaEntradaUtcDescIdAsistenciaDesc(
            Long idUsuario, EstadoAsistenciaLaboral estado);

    List<AsistenciaLaboral> findByUsuario_IdUsuarioAndFechaLaboralAndEstadoOrderByFechaEntradaUtcAsc(
            Long idUsuario, LocalDate fechaLaboral, EstadoAsistenciaLaboral estado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AsistenciaLaboral> findFirstByUsuario_IdUsuarioAndFechaSalidaUtcIsNullAndEstadoOrderByFechaEntradaUtcDesc(
            Long idUsuario, EstadoAsistenciaLaboral estado);

    /** Existing intervals intersecting [inicio, +infinity). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsistenciaLaboral a "
            + "where a.usuario.idUsuario = :idUsuario "
            + "and a.estado = :estado "
            + "and (:idExcluir is null or a.idAsistencia <> :idExcluir) "
            + "and (a.fechaSalidaUtc is null or a.fechaSalidaUtc > :inicio)")
    List<AsistenciaLaboral> findSolapamientosSesionAbierta(
            @Param("idUsuario") Long idUsuario,
            @Param("inicio") java.time.LocalDateTime inicio,
            @Param("estado") EstadoAsistenciaLaboral estado,
            @Param("idExcluir") Long idExcluir);

    /** Existing intervals intersecting the half-open interval [inicio, fin). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsistenciaLaboral a "
            + "where a.usuario.idUsuario = :idUsuario "
            + "and a.estado = :estado "
            + "and a.idAsistencia <> :idExcluir "
            + "and a.fechaEntradaUtc < :fin "
            + "and (a.fechaSalidaUtc is null or a.fechaSalidaUtc > :inicio)")
    List<AsistenciaLaboral> findSolapamientos(
            @Param("idUsuario") Long idUsuario,
            @Param("inicio") java.time.LocalDateTime inicio,
            @Param("fin") java.time.LocalDateTime fin,
            @Param("idExcluir") Long idExcluir,
            @Param("estado") EstadoAsistenciaLaboral estado);
}
