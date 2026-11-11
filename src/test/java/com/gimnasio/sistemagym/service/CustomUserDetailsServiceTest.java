package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class CustomUserDetailsServiceTest {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final CustomUserDetailsService service =
            new CustomUserDetailsService(usuarioRepository);

    @Test
    void cargaAutoridadDeEntrenadorParaAutenticacion() {
        Usuario usuario = usuario("ENTRENADOR", "ACTIVO");
        when(usuarioRepository.findByCorreo("entrenador@example.test"))
                .thenReturn(Optional.of(usuario));

        UserDetails detalles = service.loadUserByUsername("entrenador@example.test");

        assertEquals("ROLE_ENTRENADOR",
                detalles.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void conservaAutenticacionDeNutricionista() {
        Usuario usuario = usuario("NUTRICIONISTA", "ACTIVO");
        when(usuarioRepository.findByCorreo("nutri@example.test"))
                .thenReturn(Optional.of(usuario));

        UserDetails detalles = service.loadUserByUsername("nutri@example.test");

        assertEquals("ROLE_NUTRICIONISTA",
                detalles.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void sigueRechazandoRolesNoHabilitados() {
        when(usuarioRepository.findByCorreo("otro@example.test"))
                .thenReturn(Optional.of(usuario("OTRO", "ACTIVO")));

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("otro@example.test"));
    }

    private Usuario usuario(String nombreRol, String estado) {
        Rol rol = new Rol();
        rol.setNombre(nombreRol);

        Usuario usuario = new Usuario();
        usuario.setCorreo(nombreRol.equals("NUTRICIONISTA")
                ? "nutri@example.test"
                : nombreRol.equals("ENTRENADOR")
                        ? "entrenador@example.test" : "otro@example.test");
        usuario.setContrasenaHash("hash-no-utilizado-en-este-test");
        usuario.setEstado(estado);
        usuario.setRol(rol);
        return usuario;
    }
}
