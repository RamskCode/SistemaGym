package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.SesionRefresh;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SesionRefreshRepository extends JpaRepository<SesionRefresh, String> {
    boolean existsByIdAndExpiraEnAfter(String id, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SesionRefresh s join fetch s.usuario u join fetch u.rol "
            + "where s.tokenHash = :hash")
    Optional<SesionRefresh> buscarBloqueadaPorHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from SesionRefresh s where s.usuario.idUsuario = :usuarioId")
    int revocarTodasDeUsuario(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from SesionRefresh s where s.expiraEn < :limite")
    int eliminarExpiradas(@Param("limite") Instant limite);
}
