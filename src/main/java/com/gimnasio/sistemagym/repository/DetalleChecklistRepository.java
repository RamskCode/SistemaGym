package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.DetalleChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleChecklistRepository extends JpaRepository<DetalleChecklist, Long> {

    List<DetalleChecklist> findByChecklist_IdChecklistInOrderByIdDetalleAsc(
            List<Long> checklistIds);

    List<DetalleChecklist> findByChecklist_IdChecklistOrderByIdDetalleAsc(Long checklistId);
}
