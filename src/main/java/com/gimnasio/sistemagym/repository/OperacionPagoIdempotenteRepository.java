package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.OperacionPagoIdempotente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface OperacionPagoIdempotenteRepository
        extends JpaRepository<OperacionPagoIdempotente, String> {

    interface CurrentOutcome {
        String getEstadoActual();
        String getResultadoPayload();
        String getRechazoTipo();
        String getRechazoMensaje();
    }

    Optional<OperacionPagoIdempotente> findByUsuario_IdUsuarioAndClaveIdempotencia(
            Long idUsuario, String claveIdempotencia);

    Optional<OperacionPagoIdempotente> findByClaveIdempotenciaAndUsuario_IdUsuario(
            String claveIdempotencia, Long idUsuario);

    Optional<OperacionPagoIdempotente> findByIdOperacionAndUsuario_IdUsuario(
            String idOperacion, Long idUsuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OperacionPagoIdempotente o "
            + "where o.idOperacion = :id and o.usuario.idUsuario = :usuario")
    Optional<OperacionPagoIdempotente> findLockedForOwner(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("usuario") Long usuario);

    @org.springframework.data.jpa.repository.Query(value = "select estado as estadoActual, "
            + "resultado_payload as resultadoPayload, rechazo_tipo as rechazoTipo, "
            + "rechazo_mensaje as rechazoMensaje "
            + "from operaciones_pago_idempotentes where id_operacion = :id and id_usuario = :usuario for update",
            nativeQuery = true)
    Optional<CurrentOutcome> lockCurrentOutcomeForOwner(
            @org.springframework.data.repository.query.Param("id") String id,
            @org.springframework.data.repository.query.Param("usuario") Long usuario);

}
