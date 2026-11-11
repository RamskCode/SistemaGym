package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.InvitacionUsuario;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitacionUsuarioRepository extends JpaRepository<InvitacionUsuario, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InvitacionUsuario i join fetch i.usuario u join fetch u.rol where i.tokenHash = :hash")
    Optional<InvitacionUsuario> findLockedByTokenHash(@Param("hash") String hash);

    @Query("select i from InvitacionUsuario i join fetch i.usuario u where i.tokenHash = :hash")
    Optional<InvitacionUsuario> findByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update InvitacionUsuario i set i.fechaInvalidacion = :now where i.usuario.idUsuario = :userId and i.fechaUso is null and i.fechaInvalidacion is null")
    int invalidateOpen(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    long countByUsuarioIdUsuarioAndFechaCreacionAfter(Long userId, LocalDateTime since);
}
