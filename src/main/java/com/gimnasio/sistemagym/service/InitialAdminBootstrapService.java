package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.config.InitialAdminProperties;
import com.gimnasio.sistemagym.dto.InitialAdminRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Configuracion;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.ConfiguracionRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes first-administrator creation against all application admin-role changes. */
@Service
public class InitialAdminBootstrapService {

    private static final String ROL_ADMINISTRADOR = "ADMINISTRADOR";
    private static final String ONBOARDING_COMPLETADO = "ONBOARDING_ADMIN_COMPLETADO";

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final Validator validator;
    private final ConfiguracionRepository configuracionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    public InitialAdminBootstrapService(
            RolRepository rolRepository,
            UsuarioRepository usuarioRepository,
            Validator validator, ConfiguracionRepository configuracionRepository,
            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.validator = validator;
        this.configuracionRepository = configuracionRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
    }

    @Transactional
    public void initialize(InitialAdminProperties properties) {
        Rol rolAdministrador = rolRepository.findLockedByNombreIgnoreCase(ROL_ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "Bootstrap detenido: no existe el rol ADMINISTRADOR en roles."));

        if (onboardingCompletado()) return;

        // Count every account assigned to the role, including inactive accounts.
        // The locked role row serializes concurrent application bootstrap/admin changes.
        if (usuarioRepository.countByRoleId(rolAdministrador.getIdRol()) > 0) {
            marcarOnboardingCompletado();
            return;
        }

        validarConfiguracion(properties);
        InitialAdminRequest solicitud = new InitialAdminRequest(
                properties.getName(),
                properties.getLastName(),
                properties.getEmail(),
                properties.getPassword());
        validarSolicitud(solicitud);
        crearAdministrador(rolAdministrador, solicitud);
        marcarOnboardingCompletado();
    }

    /** Called by the protected first-install flow; shares the same role lock and user creation path. */
    @Transactional
    public void crearPrimerAdministrador(InitialAdminRequest solicitud) {
        Rol rolAdministrador = rolRepository.findLockedByNombreIgnoreCase(ROL_ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el rol ADMINISTRADOR en roles."));
        if (onboardingCompletado()) {
            throw new ConflictoException("La configuración inicial ya fue completada.");
        }
        if (usuarioRepository.countByRoleId(rolAdministrador.getIdRol()) > 0) {
            marcarOnboardingCompletado();
            throw new ConflictoException("La configuración inicial ya fue completada.");
        }
        validarSolicitud(solicitud);
        crearAdministrador(rolAdministrador, solicitud);
        marcarOnboardingCompletado();
    }

    private void crearAdministrador(Rol rol, InitialAdminRequest request) {
        passwordPolicy.validate(request.contrasena());
        String email = request.correo().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByCorreoIgnoreCase(email)) {
            throw new ConflictoException("Ya existe un usuario con ese correo.");
        }
        com.gimnasio.sistemagym.entity.Usuario admin = new com.gimnasio.sistemagym.entity.Usuario();
        admin.setRol(rol);
        admin.setNombres(request.nombres().trim());
        admin.setApellidos(request.apellidos().trim());
        admin.setCorreo(email);
        admin.setContrasenaHash(passwordEncoder.encode(request.contrasena()));
        admin.setEstado("ACTIVO");
        // Deliberately preserve the first-install administrator's legacy NULL document.
        usuarioRepository.saveAndFlush(admin);
    }

    /** Permanently closes onboarding if any administrator already exists. */
    @Transactional
    public boolean reconciliarEstadoOnboarding() {
        Rol rolAdministrador = rolRepository.findLockedByNombreIgnoreCase(ROL_ADMINISTRADOR)
                .orElse(null);
        if (rolAdministrador == null) return true;
        if (onboardingCompletado()) return true;
        if (usuarioRepository.countByRoleId(rolAdministrador.getIdRol()) > 0) {
            marcarOnboardingCompletado();
            return true;
        }
        return false;
    }

    private boolean onboardingCompletado() {
        return configuracionRepository.findByClave(ONBOARDING_COMPLETADO).isPresent();
    }

    private void marcarOnboardingCompletado() {
        if (configuracionRepository.findByClave(ONBOARDING_COMPLETADO).isPresent()) return;
        Configuracion marker = new Configuracion();
        marker.setClave(ONBOARDING_COMPLETADO);
        marker.setValor("SI");
        marker.setDescripcion("Marcador interno e irreversible de configuración inicial");
        configuracionRepository.saveAndFlush(marker);
    }

    private void validarConfiguracion(InitialAdminProperties properties) {
        if (properties == null) {
            throw new IllegalStateException(
                    "Bootstrap detenido: faltan las variables INITIAL_ADMIN_NAME, "
                            + "INITIAL_ADMIN_LAST_NAME, INITIAL_ADMIN_EMAIL e "
                            + "INITIAL_ADMIN_PASSWORD.");
        }

        StringBuilder missing = new StringBuilder();
        agregarSiVacio(missing, properties.getName(), "INITIAL_ADMIN_NAME");
        agregarSiVacio(missing, properties.getLastName(), "INITIAL_ADMIN_LAST_NAME");
        agregarSiVacio(missing, properties.getEmail(), "INITIAL_ADMIN_EMAIL");
        agregarSiVacio(missing, properties.getPassword(), "INITIAL_ADMIN_PASSWORD");
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Bootstrap detenido: faltan variables de entorno: " + missing + ".");
        }
    }

    private void agregarSiVacio(StringBuilder missing, String value, String variable) {
        if (value == null || value.isBlank()) {
            if (!missing.isEmpty()) {
                missing.append(", ");
            }
            missing.append(variable);
        }
    }

    private void validarSolicitud(InitialAdminRequest solicitud) {
        Set<ConstraintViolation<InitialAdminRequest>> violations = validator.validate(solicitud);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .sorted(java.util.Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            // Do not attach violations as a cause: providers may include rejected values.
            throw new IllegalStateException(
                    "Bootstrap detenido: configuración inicial inválida (" + details + ").");
        }
    }
}
