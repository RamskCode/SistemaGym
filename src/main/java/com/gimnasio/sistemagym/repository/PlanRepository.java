package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByNombreIgnoreCase(String nombre);

    List<Plan> findByEstado(String estado);

    List<Plan> findByNombreContainingIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCase(String nombre);
}
