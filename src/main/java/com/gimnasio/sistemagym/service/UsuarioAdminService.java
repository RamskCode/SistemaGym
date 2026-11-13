package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarUsuarioRequest;
import com.gimnasio.sistemagym.dto.CambiarContrasenaUsuarioRequest;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.dto.UsuarioRespuesta;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.util.DocumentoIdentidad;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import com.gimnasio.sistemagym.repository.InvitacionUsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class UsuarioAdminService {

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ACTIVO = "ACTIVO";
    private static final String INACTIVO = "INACTIVO";
    private static final java.util.Set<String> ROLES_PERMITIDOS = java.util.Set.of(
            "ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA");

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final InvitacionUsuarioRepository invitaciones;
    private final RefreshSessionService refreshSessions;

    @Autowired
    public UsuarioAdminService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            InvitacionUsuarioRepository invitaciones,
            ObjectProvider<RefreshSessionService> refreshSessions) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.invitaciones = invitaciones;
        this.refreshSessions = refreshSessions.getIfAvailable();
    }

    public UsuarioAdminService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            InvitacionUsuarioRepository invitaciones) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.invitaciones = invitaciones;
        this.refreshSessions = null;
    }

    public UsuarioAdminService(UsuarioRepository usuarioRepository,
            RolRepository rolRepository, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        this(usuarioRepository, rolRepository, passwordEncoder, passwordPolicy, null);
    }

    public UsuarioAdminService(UsuarioRepository usuarioRepository, RolRepository rolRepository,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
            InvitacionUsuarioRepository invitaciones, RefreshSessionService refreshSessions) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.invitaciones = invitaciones;
        this.refreshSessions = refreshSessions;
    }

    public List<UsuarioRespuesta> listarTodos() {
        return usuarioRepository.findAllByOrderByIdUsuarioAsc()
                .stream()
                .map(this::aRespuesta)
                .toList();
    }

    public UsuarioRespuesta buscarPorId(Long id) {
        validarId(id);
        return usuarioRepository.findByIdUsuario(id)
                .map(this::aRespuesta)
                .orElseThrow(() -> noEncontrado(id));
    }

    @Transactional
    public UsuarioRespuesta crear(CrearUsuarioRequest solicitud) {
        validarRolSolicitud(solicitud.rol());

        String correo = normalizarCorreo(solicitud.correo());
        Documento documento = normalizarDocumento(
                solicitud.tipoDocumento(), solicitud.numeroDocumento());
        if (documento.tipo() == null) {
            throw new IllegalArgumentException(
                    "El tipo y número de documento son obligatorios para nuevos usuarios.");
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ConflictoException("Ya existe un usuario con ese correo.");
        }
        verificarDocumentoDisponible(documento, null);

        Rol rol = obtenerRolParaCreacion(solicitud.rol());
        Usuario usuario = new Usuario();
        usuario.setNombres(solicitud.nombres().trim());
        usuario.setApellidos(solicitud.apellidos().trim());
        usuario.setCorreo(correo);
        // Never provide an administrator-selected initial password. This random
        // hash is unusable as a credential while the account is pending.
        usuario.setContrasenaHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        usuario.setRol(rol);
        usuario.setEstado("PENDIENTE");
        aplicarDocumento(usuario, documento);

        try {
            return aRespuesta(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo crear el usuario porque alguno de sus datos ya existe "
                            + "o no cumple las restricciones.");
        }
    }

    @Transactional
    public UsuarioRespuesta actualizar(Long id, ActualizarUsuarioRequest solicitud) {
        validarId(id);
        String correo = normalizarCorreo(solicitud.correo());
        Usuario usuario = buscarBloqueado(id);

        Documento documento = null;
        boolean actualizarDocumento = solicitud.algunCampoDocumentoIncluido();
        if (actualizarDocumento) {
            documento = normalizarDocumento(
                    solicitud.tipoDocumento(), solicitud.numeroDocumento());
            verificarDocumentoDisponible(documento, id);
        }

        boolean correoCambio = !normalizarCorreo(usuario.getCorreo()).equals(correo);
        if (correoCambio
                && usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new ConflictoException("Ya existe un usuario con ese correo.");
        }

        usuario.setNombres(solicitud.nombres().trim());
        usuario.setApellidos(solicitud.apellidos().trim());
        usuario.setCorreo(correo);
        if (correoCambio && "PENDIENTE".equals(usuario.getEstado()) && invitaciones != null) {
            invitaciones.invalidateOpen(id, java.time.LocalDateTime.now());
        }
        if (actualizarDocumento) {
            aplicarDocumento(usuario, documento);
        }
        try {
            return aRespuesta(usuarioRepository.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo actualizar el usuario porque alguno de sus datos "
                            + "ya existe o no cumple las restricciones.");
        }
    }

    @Transactional
    public UsuarioRespuesta cambiarRol(Long id, String nombreRol) {
        validarId(id);
        if (nombreRol == null || nombreRol.isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio.");
        }

        Rol rolAdministrador = bloquearRolAdministrador();
        Usuario usuario = buscarBloqueado(id);
        Rol nuevoRol = nombreRol.trim().equalsIgnoreCase(ROL_ADMINISTRADOR)
                ? rolAdministrador
                : obtenerRol(nombreRol);

        if (esActivoYDelRol(usuario, rolAdministrador)
                && !nuevoRol.getIdRol().equals(rolAdministrador.getIdRol())) {
            asegurarQueQuedeAdministradorActivo(rolAdministrador);
        }

        usuario.setRol(nuevoRol);
        return aRespuesta(usuarioRepository.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioRespuesta activar(Long id) {
        validarId(id);
        bloquearRolAdministrador();
        Usuario usuario = buscarBloqueado(id);
        if ("PENDIENTE".equals(usuario.getEstado())) {
            throw new ConflictoException("La cuenta pendiente solo se activa al completar la invitación.");
        }
        usuario.setEstado(ACTIVO);
        return aRespuesta(usuarioRepository.saveAndFlush(usuario));
    }

    @Transactional
    public UsuarioRespuesta desactivar(Long id) {
        validarId(id);
        Rol rolAdministrador = bloquearRolAdministrador();
        Usuario usuario = buscarBloqueado(id);
        if ("PENDIENTE".equals(usuario.getEstado())) {
            throw new ConflictoException("Una cuenta pendiente no se puede desactivar; puedes reenviar su invitación.");
        }

        if (esActivoYDelRol(usuario, rolAdministrador)) {
            asegurarQueQuedeAdministradorActivo(rolAdministrador);
        }

        usuario.setEstado(INACTIVO);
        UsuarioRespuesta respuesta = aRespuesta(usuarioRepository.saveAndFlush(usuario));
        if (refreshSessions != null) refreshSessions.revocarUsuario(usuario.getIdUsuario());
        return respuesta;
    }

    @Transactional
    public void cambiarContrasena(Long id, CambiarContrasenaUsuarioRequest solicitud) {
        validarId(id);
        passwordPolicy.validate(solicitud.contrasena());
        Usuario usuario = buscarBloqueado(id);
        usuario.setContrasenaHash(passwordEncoder.encode(solicitud.contrasena()));
        usuario.setVersionCredenciales(usuario.getVersionCredenciales() + 1);
        usuarioRepository.saveAndFlush(usuario);
        if (refreshSessions != null) refreshSessions.revocarUsuario(usuario.getIdUsuario());
    }

    private Rol obtenerRolParaCreacion(String nombreRol) {
        if (nombreRol.trim().equalsIgnoreCase(ROL_ADMINISTRADOR)) {
            return bloquearRolAdministrador();
        }
        return obtenerRol(nombreRol);
    }

    private Rol obtenerRol(String nombreRol) {
        String normalizado = nombreRol.trim().toUpperCase(Locale.ROOT);
        Rol rol = rolRepository.findByNombreIgnoreCase(normalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El rol indicado no existe."));
        if (!ROLES_PERMITIDOS.contains(normalizado)) {
            throw new IllegalArgumentException("El rol indicado no existe.");
        }
        return rol;
    }

    private Rol bloquearRolAdministrador() {
        return rolRepository.findLockedByNombreIgnoreCase(ROL_ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol ADMINISTRADOR no está configurado."));
    }

    private Usuario buscarBloqueado(Long id) {
        return usuarioRepository.findLockedById(id)
                .orElseThrow(() -> noEncontrado(id));
    }

    private void asegurarQueQuedeAdministradorActivo(Rol rolAdministrador) {
        long cantidad = usuarioRepository.countByRoleIdAndEstadoIgnoreCase(
                rolAdministrador.getIdRol(), ACTIVO);
        if (cantidad <= 1) {
            throw new ConflictoException(
                    "No se puede quitar el último administrador activo.");
        }
    }

    private boolean esActivoYDelRol(Usuario usuario, Rol rolAdministrador) {
        return ACTIVO.equalsIgnoreCase(usuario.getEstado())
                && usuario.getRol().getIdRol().equals(rolAdministrador.getIdRol());
    }

    private UsuarioRespuesta aRespuesta(Usuario usuario) {
        return new UsuarioRespuesta(
                usuario.getIdUsuario(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                usuario.getEstado(),
                usuario.getFechaCreacion(),
                usuario.getTipoDocumento(),
                usuario.getNumeroDocumento());
    }

    private Documento normalizarDocumento(
            TipoDocumento tipoDocumento, String numeroDocumento) {
        if (tipoDocumento == null && numeroDocumento == null) {
            return new Documento(null, null);
        }
        if (tipoDocumento == null || numeroDocumento == null) {
            throw new IllegalArgumentException(
                    "El tipo y número de documento deben informarse juntos.");
        }
        if (numeroDocumento.isEmpty() || numeroDocumento.length() > 20
                || !DocumentoIdentidad.esValido(tipoDocumento, numeroDocumento)) {
            throw new IllegalArgumentException(
                    "El documento no cumple el formato indicado para DNI o CE.");
        }

        String normalizado = DocumentoIdentidad.normalizar(tipoDocumento, numeroDocumento);
        return new Documento(tipoDocumento, normalizado);
    }

    private void verificarDocumentoDisponible(Documento documento, Long idUsuario) {
        if (documento.tipo() == null) {
            return;
        }
        boolean duplicado = idUsuario == null
                ? usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(
                        documento.tipo(), documento.numero())
                : usuarioRepository.existsByTipoDocumentoAndNumeroDocumentoAndIdUsuarioNot(
                        documento.tipo(), documento.numero(), idUsuario);
        if (duplicado) {
            throw new ConflictoException(
                    "Ya existe un usuario con ese tipo y número de documento.");
        }
    }

    private void aplicarDocumento(Usuario usuario, Documento documento) {
        usuario.setTipoDocumento(documento.tipo());
        usuario.setNumeroDocumento(documento.numero());
    }

    private record Documento(TipoDocumento tipo, String numero) {
    }

    private String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("El correo es obligatorio.");
        }
        return correo.trim().toLowerCase(Locale.ROOT);
    }

    private void validarRolSolicitud(String nombreRol) {
        if (nombreRol == null || nombreRol.isBlank()) {
            throw new IllegalArgumentException("El rol es obligatorio.");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor que cero.");
        }
    }

    private RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException(
                "No existe un usuario con el ID " + id + ".");
    }
}
