package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarPlanRequest;
import com.gimnasio.sistemagym.dto.RegistrarPlanRequest;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    public List<Plan> listarTodos() {
        return planRepository.findAll();
    }

    public Optional<Plan> buscarPorId(Long id) {
        return planRepository.findById(id);
    }

    public Optional<Plan> buscarPorNombre(String nombre) {
        return planRepository.findByNombreIgnoreCase(nombre);
    }

    public List<Plan> listarPorEstado(String estado) {
        return planRepository.findByEstado(estado);
    }

    public List<Plan> buscarPorNombreParcial(String nombre) {
        return planRepository.findByNombreContainingIgnoreCase(nombre);
    }

    @Transactional
    public Plan registrar(RegistrarPlanRequest solicitud) {

        validarPlan(
                solicitud.nombre(),
                solicitud.precio(),
                solicitud.duracionDias()
        );

        if (planRepository.existsByNombreIgnoreCase(solicitud.nombre().trim())) {
            throw new IllegalArgumentException(
                    "Ya existe un plan con ese nombre."
            );
        }

        Plan plan = new Plan();
        plan.setNombre(solicitud.nombre().trim());
        plan.setDescripcion(solicitud.descripcion());
        plan.setPrecio(solicitud.precio());
        plan.setDuracionDias(solicitud.duracionDias());
        plan.setEstado("ACTIVO");

        return planRepository.save(plan);
    }

    @Transactional
    public Plan actualizar(Long id, ActualizarPlanRequest datosActualizados) {

        validarPlan(
                datosActualizados.nombre(),
                datosActualizados.precio(),
                datosActualizados.duracionDias()
        );

        Plan planExistente = planRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un plan con el ID " + id + "."
                ));

        String nombreNuevo = datosActualizados.nombre().trim();

        Optional<Plan> planConNombre =
                planRepository.findByNombreIgnoreCase(nombreNuevo);

        if (planConNombre.isPresent()
                && !planConNombre.get().getIdPlan().equals(id)) {
            throw new IllegalArgumentException(
                    "El nombre ingresado ya pertenece a otro plan."
            );
        }

        planExistente.setNombre(nombreNuevo);
        planExistente.setDescripcion(datosActualizados.descripcion());
        planExistente.setPrecio(datosActualizados.precio());
        planExistente.setDuracionDias(datosActualizados.duracionDias());

        return planRepository.save(planExistente);
    }

    @Transactional
    public Plan desactivar(Long id) {

        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un plan con el ID " + id + "."
                ));

        plan.setEstado("INACTIVO");

        return planRepository.save(plan);
    }

    private void validarPlan(
            String nombre,
            BigDecimal precio,
            Integer duracionDias) {

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del plan es obligatorio."
            );
        }

        if (precio == null || precio.signum() <= 0) {
            throw new IllegalArgumentException(
                    "El precio debe ser mayor que cero."
            );
        }

        if (duracionDias == null || duracionDias <= 0) {
            throw new IllegalArgumentException(
                    "La duración debe ser mayor que cero."
            );
        }
    }
}
