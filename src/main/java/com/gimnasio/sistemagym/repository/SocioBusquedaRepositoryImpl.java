package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Socio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;

@Repository
public class SocioBusquedaRepositoryImpl implements SocioBusquedaRepository {

    private static final char LIKE_ESCAPE = '!';
    private static final List<String> CAMPOS_BUSQUEDA =
            List.of("nombres", "apellidos", "dni", "numeroDocumento", "codigoSocio");

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.database.provider:mysql}")
    private String databaseProvider;

    @Override
    public List<Socio> buscarGlobal(String texto) {
        if ("postgresql".equalsIgnoreCase(databaseProvider)) {
            return buscarGlobalPostgres(texto);
        }
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

    private List<Socio> buscarGlobalPostgres(String texto) {
        List<String> columnas = List.of("nombres", "apellidos", "dni", "numero_documento",
                "codigo_socio");
        List<String> palabras = texto.trim().split("\\s+").length == 0
                ? List.of() : List.of(texto.trim().split("\\s+"));
        if (palabras.isEmpty()) return List.of();

        StringBuilder sql = new StringBuilder("SELECT DISTINCT s.* FROM socios s WHERE ");
        for (int i = 0; i < palabras.size(); i++) {
            if (i > 0) sql.append(" AND ");
            sql.append('(');
            for (int j = 0; j < columnas.size(); j++) {
                if (j > 0) sql.append(" OR ");
                sql.append("lower(extensions.unaccent(coalesce(s.").append(columnas.get(j))
                        .append(", '') COLLATE \"C\")) COLLATE \"C\" LIKE "
                                + "lower(extensions.unaccent(? COLLATE \"C\")) COLLATE \"C\" ESCAPE '!'");
            }
            sql.append(')');
        }
        var consulta = entityManager.createNativeQuery(sql.toString(), Socio.class);
        int indice = 1;
        for (String palabra : palabras) {
            String patron = "%" + escaparLike(palabra).toLowerCase(java.util.Locale.ROOT) + "%";
            for (int ignored = 0; ignored < columnas.size(); ignored++) {
                consulta.setParameter(indice++, patron);
            }
        }
        @SuppressWarnings("unchecked")
        List<Socio> resultados = consulta.getResultList();
        return resultados;
    }

    private String escaparLike(String valor) {
        return valor.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
