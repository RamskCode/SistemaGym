package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarMiCuentaRequest;
import com.gimnasio.sistemagym.dto.CambiarMiContrasenaRequest;
import com.gimnasio.sistemagym.dto.MiCuentaRespuesta;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MiCuentaService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshSessionService refreshSessions;

    @Autowired
    public MiCuentaService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, ObjectProvider<RefreshSessionService> refreshSessions) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = refreshSessions.getIfAvailable();
    }

    public MiCuentaService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = null;
    }

    public MiCuentaService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, RefreshSessionService refreshSessions) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshSessions = refreshSessions;
    }

    public MiCuentaRespuesta consultar(String correoAutenticado) {
        return respuesta(buscar(correoAutenticado));
    }

    @Transactional
    public MiCuentaRespuesta actualizar(String correoAutenticado,
            ActualizarMiCuentaRequest solicitud) {
        if (solicitud == null || !solicitud.tieneCambios()) {
            throw new IllegalArgumentException("Indica al menos un dato personal para actualizar.");
        }
        Usuario usuario = usuarios.findLockedByCorreoIgnoreCase(correoAutenticado)
                .orElseThrow(MiCuentaService::cuentaNoEncontrada);

        if (solicitud.nombres() != null) usuario.setNombres(solicitud.nombres().trim());
        if (solicitud.apellidos() != null) usuario.setApellidos(solicitud.apellidos().trim());
        if (solicitud.tipoDocumentoIncluido()) {
            TipoDocumento tipo = solicitud.tipoDocumento();
            String numero = solicitud.numeroDocumento();
            if (tipo != null && usuarios.existsByTipoDocumentoAndNumeroDocumentoAndIdUsuarioNot(
                    tipo, numero, usuario.getIdUsuario())) {
                throw new ConflictoException("Ese documento ya está registrado en otra cuenta.");
            }
            usuario.setTipoDocumento(tipo);
            usuario.setNumeroDocumento(numero);
        }

        try {
            return respuesta(usuarios.saveAndFlush(usuario));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo actualizar el perfil porque el documento ya está registrado o no es válido.");
        }
    }

    @Transactional
    public void cambiarContrasena(String correoAutenticado,
            CambiarMiContrasenaRequest solicitud) {
        Usuario usuario = usuarios.findLockedByCorreoIgnoreCase(correoAutenticado)
                .orElseThrow(MiCuentaService::cuentaNoEncontrada);
        if (!passwordEncoder.matches(solicitud.contrasenaActual(), usuario.getContrasenaHash())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        passwordPolicy.validate(solicitud.contrasenaNueva());
        if (passwordEncoder.matches(solicitud.contrasenaNueva(), usuario.getContrasenaHash())) {
            throw new IllegalArgumentException("La contraseña nueva debe ser distinta de la actual.");
        }
        usuario.setContrasenaHash(passwordEncoder.encode(solicitud.contrasenaNueva()));
        usuario.setVersionCredenciales(usuario.getVersionCredenciales() + 1);
        usuarios.saveAndFlush(usuario);
        if (refreshSessions != null) refreshSessions.revocarUsuario(usuario.getIdUsuario());
    }

    private Usuario buscar(String correoAutenticado) {
        if (correoAutenticado == null || correoAutenticado.isBlank()) {
            throw cuentaNoEncontrada();
        }
        return usuarios.findByCorreoIgnoreCase(correoAutenticado)
                .orElseThrow(MiCuentaService::cuentaNoEncontrada);
    }

    private MiCuentaRespuesta respuesta(Usuario usuario) {
        return new MiCuentaRespuesta(usuario.getNombres(), usuario.getApellidos(),
                usuario.getCorreo(), usuario.getRol().getNombre(),
                usuario.getTipoDocumento(), usuario.getNumeroDocumento());
    }

    private static RecursoNoEncontradoException cuentaNoEncontrada() {
        return new RecursoNoEncontradoException("No se encontró la cuenta autenticada.");
    }
}
