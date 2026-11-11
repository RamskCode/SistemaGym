package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {

    Optional<Configuracion> findByClave(String clave);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Configuracion c where c.clave = :clave")
    Optional<Configuracion> findLockedByClave(@Param("clave") String clave);
}
