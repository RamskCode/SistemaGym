package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.AsistenciaLaboral;
import com.gimnasio.sistemagym.entity.EstadoAsistenciaLaboral;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds only predicates for filters that are present, avoiding untyped null bind parameters. */
public final class AsistenciaLaboralSpecifications {

    private AsistenciaLaboralSpecifications() { }

    public static Specification<AsistenciaLaboral> conFiltros(
            Long idEmpleado, LocalDate fechaDesde, LocalDate fechaHasta,
            EstadoAsistenciaLaboral estado) {
        return (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (idEmpleado != null) {
                predicates.add(builder.equal(root.get("usuario").get("idUsuario"), idEmpleado));
            }
            if (fechaDesde != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("fechaLaboral"), fechaDesde));
            }
            if (fechaHasta != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("fechaLaboral"), fechaHasta));
            }
            if (estado != null) {
                predicates.add(builder.equal(root.get("estado"), estado));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
