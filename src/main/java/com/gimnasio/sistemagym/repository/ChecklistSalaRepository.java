package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.ChecklistSala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistSalaRepository extends JpaRepository<ChecklistSala, Long> {

    List<ChecklistSala> findByEntrenador_IdUsuarioOrderByFechaHoraDescIdChecklistDesc(
            Long idEntrenador);

    List<ChecklistSala> findAllByOrderByFechaHoraDescIdChecklistDesc();

    Optional<ChecklistSala> findByIdChecklistAndEntrenador_IdUsuario(
            Long idChecklist, Long idEntrenador);
}
