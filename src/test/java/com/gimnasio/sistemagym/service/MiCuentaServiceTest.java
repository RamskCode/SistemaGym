package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarMiCuentaRequest;
import com.gimnasio.sistemagym.dto.CambiarMiContrasenaRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MiCuentaServiceTest {
    private static final String CURRENT = "CurrentStrong!42";
    private UsuarioRepository usuarios;
    private BCryptPasswordEncoder encoder;
    private MiCuentaService service;
    private RefreshSessionService refreshSessions;
    private Usuario current;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        encoder = new BCryptPasswordEncoder(4);
        refreshSessions = mock(RefreshSessionService.class);
        service = new MiCuentaService(usuarios, encoder, new PasswordPolicy(), refreshSessions);
        Rol role = new Rol();
        role.setNombre("ENTRENADOR");
        current = new Usuario();
        current.setNombres("Ana");
        current.setApellidos("Prueba");
        current.setCorreo("ana@example.test");
        current.setRol(role);
        current.setEstado("ACTIVO");
        current.setContrasenaHash(encoder.encode(CURRENT));
        try {
            var id = Usuario.class.getDeclaredField("idUsuario");
            id.setAccessible(true);
            id.set(current, 23L);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        when(usuarios.findByCorreoIgnoreCase("ana@example.test")).thenReturn(Optional.of(current));
        when(usuarios.findLockedByCorreoIgnoreCase("ana@example.test")).thenReturn(Optional.of(current));
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void consultaExclusivamenteLaIdentidadDerivadaDelCorreoAutenticado() {
        var profile = service.consultar("ana@example.test");
        assertEquals("Ana", profile.nombres());
        assertEquals("ana@example.test", profile.correo());
        assertEquals("ENTRENADOR", profile.rol());
        verify(usuarios).findByCorreoIgnoreCase("ana@example.test");
    }

    @Test
    void actualizaDatosPropiosSinTocarCorreoRolEstadoNiHash() {
        String hash = current.getContrasenaHash();
        ActualizarMiCuentaRequest request = new ActualizarMiCuentaRequest();
        request.setNombres(" Ana María ");
        request.setApellidos(" Nueva ");

        var profile = service.actualizar("ana@example.test", request);

        assertEquals("Ana María", profile.nombres());
        assertEquals("Nueva", profile.apellidos());
        assertEquals("ana@example.test", profile.correo());
        assertEquals("ENTRENADOR", profile.rol());
        assertEquals("ACTIVO", current.getEstado());
        assertEquals(hash, current.getContrasenaHash());
        verify(usuarios).findLockedByCorreoIgnoreCase("ana@example.test");
        verify(usuarios).saveAndFlush(current);
    }

    @Test
    void actualizaDocumentoPropioYRechazaDocumentoDeOtraCuenta() {
        ActualizarMiCuentaRequest request = new ActualizarMiCuentaRequest();
        request.setTipoDocumento(TipoDocumento.DNI);
        request.setNumeroDocumento("12345678");
        var response = service.actualizar("ana@example.test", request);
        assertEquals(TipoDocumento.DNI, response.tipoDocumento());
        assertEquals("12345678", response.numeroDocumento());

        when(usuarios.existsByTipoDocumentoAndNumeroDocumentoAndIdUsuarioNot(
                TipoDocumento.CE, "12345", 23L)).thenReturn(true);
        ActualizarMiCuentaRequest conflict = new ActualizarMiCuentaRequest();
        conflict.setTipoDocumento(TipoDocumento.CE);
        conflict.setNumeroDocumento("12345");
        assertThrows(ConflictoException.class,
                () -> service.actualizar("ana@example.test", conflict));
    }

    @Test
    void cambioContrasenaVerificaActualAplicaPoliticaBCryptYRevocaSesiones() {
        long version = current.getVersionCredenciales();
        String nextPassword = "DifferentStrong!62";
        service.cambiarContrasena("ana@example.test",
                new CambiarMiContrasenaRequest(CURRENT, nextPassword));
        assertTrue(encoder.matches(nextPassword, current.getContrasenaHash()));
        assertEquals(version + 1, current.getVersionCredenciales());
        assertNotEquals(nextPassword, current.getContrasenaHash());
        verify(usuarios).saveAndFlush(current);
        verify(refreshSessions).revocarUsuario(23L);
    }

    @Test
    void rechazoDeContrasenaActualIncorrectaNoAlteraLaCuenta() {
        String hash = current.getContrasenaHash();
        assertThrows(IllegalArgumentException.class, () -> service.cambiarContrasena(
                "ana@example.test", new CambiarMiContrasenaRequest("wrong", "DifferentStrong!62")));
        assertEquals(hash, current.getContrasenaHash());
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test
    void elBackendRechazaCadaReglaIncumplidaAunqueNoHayaValidacionFrontend() {
        String[] invalidPasswords = {"Aa1!abc", "1234567!", "Abcdefg!", "Abcdefg1"};
        String originalHash = current.getContrasenaHash();
        for (String invalid : invalidPasswords) {
            assertThrows(IllegalArgumentException.class, () -> service.cambiarContrasena(
                    "ana@example.test", new CambiarMiContrasenaRequest(CURRENT, invalid)));
        }
        assertEquals(originalHash, current.getContrasenaHash());
        verify(usuarios, never()).saveAndFlush(any());
    }
}
