package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Socio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class SocioBusquedaRepositoryImpl implements SocioBusquedaRepository {

    private static final char LIKE_ESCAPE = '!';
    private static final List<String> CAMPOS_BUSQUEDA =
            List.of("nombres", "apellidos", "dni", "numeroDocumento", "codigoSocio");

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Socio> buscarGlobal(String texto) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Socio> query = criteriaBuilder.createQuery(Socio.class);
        Root<Socio> socio = query.from(Socio.class);

        List<Predicate> coincidenciasPorPalabra = new ArrayList<>();
        for (String palabra : texto.trim().split("\\s+")) {
            String patron = "%" + escaparLike(palabra) + "%";
            List<Predicate> camposCoincidentes = CAMPOS_BUSQUEDA.stream()
                    .map(campo -> criteriaBuilder.like(
                            criteriaBuilder.lower(socio.get(campo)),
                            patron.toLowerCase(java.util.Locale.ROOT),
                            LIKE_ESCAPE))
                    .toList();
            coincidenciasPorPalabra.add(criteriaBuilder.or(
                    camposCoincidentes.toArray(Predicate[]::new)));
        }

        query.select(socio)
                .where(criteriaBuilder.and(
                        coincidenciasPorPalabra.toArray(Predicate[]::new)))
                .distinct(true);
        return entityManager.createQuery(query).getResultList();
    }

    private String escaparLike(String valor) {
        return valor.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
