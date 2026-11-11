package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.RecuperacionContrasena;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecuperacionContrasenaRepository extends JpaRepository<RecuperacionContrasena, Long> {
    @Modifying
    @Query("update RecuperacionContrasena r set r.fechaInvalidacion = :ahora "
            + "where r.usuario.idUsuario = :usuarioId and r.fechaUso is null and r.fechaInvalidacion is null")
    int invalidarPendientes(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacionContrasena r join fetch r.usuario "
            + "where r.tokenHash = :tokenHash")
    Optional<RecuperacionContrasena> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<RecuperacionContrasena> findByTokenHash(String tokenHash);
}
