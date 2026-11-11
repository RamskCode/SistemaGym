
package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Rol;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombreIgnoreCase(String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Rol r where upper(r.nombre) = upper(:nombre)")
    Optional<Rol> findLockedByNombreIgnoreCase(@Param("nombre") String nombre);

}