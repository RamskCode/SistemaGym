package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.AuditoriaAsistenciaLaboral;
import com.gimnasio.sistemagym.entity.TipoOperacionAuditoriaAsistenciaLaboral;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Insert/query-only repository surface; intentionally exposes no update/delete methods. */
public interface AuditoriaAsistenciaLaboralRepository
        extends Repository<AuditoriaAsistenciaLaboral, Long> {

    <S extends AuditoriaAsistenciaLaboral> S save(S auditoria);

    boolean existsByAsistencia_IdAsistenciaAndTipoOperacion(
            Long idAsistencia, TipoOperacionAuditoriaAsistenciaLaboral tipoOperacion);

    @Query("select a.idAuditoria as idAuditoria, "
            + "a.tipoOperacion as tipoOperacion, a.motivo as motivo, "
            + "a.fechaHoraUtc as fechaHoraUtc, actor.idUsuario as idActor, "
            + "actor.nombres as nombresActor, actor.apellidos as apellidosActor, "
            + "actor.correo as correoActor, a.estadoAnterior as estadoAnterior, "
            + "a.estadoNuevo as estadoNuevo, "
            + "a.fechaEntradaAnteriorUtc as fechaEntradaAnteriorUtc, "
            + "a.fechaSalidaAnteriorUtc as fechaSalidaAnteriorUtc, "
            + "a.fechaEntradaNuevaUtc as fechaEntradaNuevaUtc, "
            + "a.fechaSalidaNuevaUtc as fechaSalidaNuevaUtc "
            + "from AuditoriaAsistenciaLaboral a join a.actor actor "
            + "where a.asistencia.idAsistencia = :idAsistencia "
            + "order by a.fechaHoraUtc asc, a.idAuditoria asc")
    List<AuditoriaAsistenciaHistorialConsulta> consultarHistorialPorAsistencia(
            @Param("idAsistencia") Long idAsistencia);
}
