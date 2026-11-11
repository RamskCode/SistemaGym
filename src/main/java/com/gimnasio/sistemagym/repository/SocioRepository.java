package com.gimnasio.sistemagym.repository;

import org.springframework.data.jpa.repository.Query;
import com.gimnasio.sistemagym.entity.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SocioRepository extends JpaRepository<Socio, Long>, SocioBusquedaRepository {

    @Query("select s from Socio s where s.fechaRegistro >= :desde and s.fechaRegistro < :hasta "
            + "order by s.fechaRegistro desc")
    Page<Socio> reportNewBetween(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, Pageable pageable);

    @Query("select year(s.fechaRegistro), month(s.fechaRegistro), count(s) from Socio s "
            + "where s.fechaRegistro >= :desde and s.fechaRegistro < :hasta "
            + "group by year(s.fechaRegistro), month(s.fechaRegistro) order by year(s.fechaRegistro), month(s.fechaRegistro)")
    List<Object[]> reportNewTimeline(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    Optional<Socio> findByCodigoSocio(String codigoSocio);

    Optional<Socio> findByDni(String dni);

    List<Socio> findByApellidosContainingIgnoreCase(String apellidos);

    List<Socio> findByEstado(String estado);

    boolean existsByCodigoSocio(String codigoSocio);

    boolean existsByDni(String dni);

    Optional<Socio> findByTipoDocumentoAndNumeroDocumento(String tipoDocumento, String numeroDocumento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Socio s where s.idSocio = :id")
    Optional<Socio> findLockedById(@Param("id") Long id);

    @Query(value = """
        SELECT MAX(CAST(SUBSTRING(codigo_socio, 5) AS UNSIGNED))
        FROM socios
        WHERE codigo_socio REGEXP '^SOC-[0-9]+$'
        """, nativeQuery = true)
    Integer obtenerUltimoNumeroCodigo();

    long countByEstadoIgnoreCase(String estado);

    long countByFechaRegistroGreaterThanEqualAndFechaRegistroLessThan(
            LocalDateTime desde, LocalDateTime hasta);

    @Query(value = """
            select distinct s from Socio s
            where upper(s.estado) = 'ACTIVO'
              and exists (
                select m.idMembresia from Membresia m
                where m.socio.idSocio = s.idSocio
                  and upper(m.estado) = 'ACTIVA'
                  and m.fechaInicio <= :hoy and m.fechaVencimiento >= :hoy
                  and (
                    coalesce((select sum(a.montoAplicado) from AplicacionPagoMembresia a
                              where a.membresia.idMembresia = m.idMembresia), 0) >= m.precioAcordado
                    or m.fechaLimitePago is null or m.fechaLimitePago >= :hoy
                  )
              )
              and (:nombre = '' or
                   replace(replace(replace(replace(replace(replace(replace(lower(s.nombres), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(s.apellidos), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(concat(s.nombres, ' ', s.apellidos)), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(concat(s.apellidos, ' ', s.nombres)), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')))
              and (:tipoDocumento = '' or
                   upper(coalesce(s.tipoDocumento, case when s.dni is not null then 'DNI' else '' end)) = :tipoDocumento)
              and (:numeroDocumento = '' or
                   coalesce(s.numeroDocumento, s.dni) like concat('%', :numeroDocumento, '%'))
            """,
            countQuery = """
            select count(distinct s.idSocio) from Socio s
            where upper(s.estado) = 'ACTIVO'
              and exists (
                select m.idMembresia from Membresia m
                where m.socio.idSocio = s.idSocio
                  and upper(m.estado) = 'ACTIVA'
                  and m.fechaInicio <= :hoy and m.fechaVencimiento >= :hoy
                  and (
                    coalesce((select sum(a.montoAplicado) from AplicacionPagoMembresia a
                              where a.membresia.idMembresia = m.idMembresia), 0) >= m.precioAcordado
                    or m.fechaLimitePago is null or m.fechaLimitePago >= :hoy
                  )
              )
              and (:nombre = '' or
                   replace(replace(replace(replace(replace(replace(replace(lower(s.nombres), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(s.apellidos), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(concat(s.nombres, ' ', s.apellidos)), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')) or
                   replace(replace(replace(replace(replace(replace(replace(lower(concat(s.apellidos, ' ', s.nombres)), 'á', 'a'), 'é', 'e'), 'í', 'i'), 'ó', 'o'), 'ú', 'u'), 'ü', 'u'), 'ñ', 'n') like lower(concat('%', :nombre, '%')))
              and (:tipoDocumento = '' or
                   upper(coalesce(s.tipoDocumento, case when s.dni is not null then 'DNI' else '' end)) = :tipoDocumento)
              and (:numeroDocumento = '' or
                   coalesce(s.numeroDocumento, s.dni) like concat('%', :numeroDocumento, '%'))
            """)
    Page<Socio> listarHabilitadosParaIngreso(
            @Param("nombre") String nombre, @Param("tipoDocumento") String tipoDocumento,
            @Param("numeroDocumento") String numeroDocumento,
            @Param("hoy") java.time.LocalDate hoy,
            Pageable pageable);

    @Query("select s from Socio s order by s.fechaRegistro desc, s.idSocio desc")
    List<Socio> findRecentRegistrations(org.springframework.data.domain.Pageable pageable);
}
