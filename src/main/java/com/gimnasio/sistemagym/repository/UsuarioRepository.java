package com.gimnasio.sistemagym.repository;

import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreo(String correo);

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u join fetch u.rol where lower(u.correo) = lower(:correo)")
    Optional<Usuario> findLockedByCorreoIgnoreCase(@Param("correo") String correo);

    @Query("select u.versionCredenciales from Usuario u where lower(u.correo) = lower(:correo)")
    Optional<Long> findCredentialVersionByCorreo(@Param("correo") String correo);

    @Query("select u.idUsuario as idEmpleado, u.nombres as nombres, "
            + "u.apellidos as apellidos, upper(r.nombre) as rol "
            + "from Usuario u join u.rol r "
            + "where upper(u.estado) = 'ACTIVO' "
            + "and upper(r.nombre) in ('ENTRENADOR', 'NUTRICIONISTA') "
            + "order by upper(u.apellidos), upper(u.nombres), u.idUsuario")
    List<PersonalSeleccionableConsulta> listarPersonalSeleccionable();

    boolean existsByCorreoIgnoreCase(String correo);

    boolean existsByTipoDocumentoAndNumeroDocumento(
            TipoDocumento tipoDocumento, String numeroDocumento);

    boolean existsByTipoDocumentoAndNumeroDocumentoAndIdUsuarioNot(
            TipoDocumento tipoDocumento, String numeroDocumento, Long idUsuario);

    @EntityGraph(attributePaths = "rol")
    List<Usuario> findAllByOrderByIdUsuarioAsc();

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByIdUsuario(Long idUsuario);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u join fetch u.rol where u.idUsuario = :id")
    Optional<Usuario> findLockedById(@Param("id") Long id);

    @Query("select count(u) from Usuario u "
            + "where u.rol.idRol = :roleId "
            + "and upper(u.estado) = upper(:estado)")
    long countByRoleIdAndEstadoIgnoreCase(
            @Param("roleId") Long roleId,
            @Param("estado") String estado);

    @Query("select count(u) from Usuario u where u.rol.idRol = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

}
