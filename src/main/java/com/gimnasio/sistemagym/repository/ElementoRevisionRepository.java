package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.ElementoRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface ElementoRevisionRepository extends JpaRepository<ElementoRevision, Long> {

    List<ElementoRevision> findAllByOrderByNombreAscIdElementoAsc();

    List<ElementoRevision> findByActivoTrueOrderByNombreAscIdElementoAsc();

    List<ElementoRevision> findAllByIdElementoInAndActivoTrue(Collection<Long> ids);
}
