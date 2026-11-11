package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.EventoAsistenciaLaboral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventoAsistenciaLaboralRepository
        extends JpaRepository<EventoAsistenciaLaboral, Long> {

    List<EventoAsistenciaLaboral> findByAsistencia_IdAsistenciaOrderByFechaHoraUtcAscIdEventoAsc(
            Long idAsistencia);

    @Query("select e.idEvento as idEvento, "
            + "e.tipoEvento as tipoEvento, e.fechaHoraUtc as fechaHoraUtc, "
            + "registrador.idUsuario as idRegistrador, "
            + "registrador.nombres as nombresRegistrador, "
            + "registrador.apellidos as apellidosRegistrador, "
            + "registrador.correo as correoRegistrador "
            + "from EventoAsistenciaLaboral e "
            + "join e.usuarioRegistrador registrador "
            + "where e.asistencia.idAsistencia = :idAsistencia "
            + "order by e.fechaHoraUtc asc, e.idEvento asc")
    List<EventoAsistenciaHistorialConsulta> consultarHistorialPorAsistencia(
            @Param("idAsistencia") Long idAsistencia);
}
