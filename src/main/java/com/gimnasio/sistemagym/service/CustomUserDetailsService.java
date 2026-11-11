
package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final java.util.Set<String> ROLES_PERMITIDOS = java.util.Set.of(
            "ADMINISTRADOR", "RECEPCIONISTA", "ENTRENADOR", "NUTRICIONISTA");

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo)
            throws UsernameNotFoundException {

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No existe un usuario con ese correo."
                ));

        if (!"ACTIVO".equalsIgnoreCase(usuario.getEstado())) {
            throw new UsernameNotFoundException(
                    "El usuario está inactivo."
            );
        }

        String rol = usuario.getRol().getNombre().toUpperCase(java.util.Locale.ROOT);
        if (!ROLES_PERMITIDOS.contains(rol)) {
            throw new UsernameNotFoundException("El rol de usuario no está habilitado.");
        }

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getContrasenaHash())
                .roles(rol)
                .build();
    }
}
