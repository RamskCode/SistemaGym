package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarUsuarioRequest;
import com.gimnasio.sistemagym.dto.CambiarContrasenaUsuarioRequest;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioAdminServiceTest {

    private static final String TEST_PASSWORD = "StrongTestPassword!42";

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    private PasswordEncoder passwordEncoder;
    private UsuarioAdminService service;
    private RefreshSessionService refreshSessions;
    private Rol administrador;
    private Rol nutricionista;
    private Rol recepcionista;
    private Rol entrenador;

    @BeforeEach
    void setUp() throws Exception {
        passwordEncoder = new BCryptPasswordEncoder(4);
        refreshSessions = mock(RefreshSessionService.class);
        service = new UsuarioAdminService(usuarioRepository, rolRepository, passwordEncoder,
                new PasswordPolicy(), null, refreshSessions);
        administrador = rol(1L, "ADMINISTRADOR");
        nutricionista = rol(2L, "NUTRICIONISTA");
        recepcionista = rol(3L, "RECEPCIONISTA");
        entrenador = rol(4L, "ENTRENADOR");
    }

    @Test
    void creaUsuarioActivoConCredencialInicialAleatoriaYHashSeguro() throws Exception {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@example.com"))
                .thenReturn(false);
        when(rolRepository.findByNombreIgnoreCase("NUTRICIONISTA"))
                .thenReturn(Optional.of(nutricionista));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario saved = invocation.getArgument(0);
                    setId(saved, 42L);
                    return saved;
                });

        var respuesta = service.crear(new CrearUsuarioRequest(
                "Ana", "Prueba", " NUEVO@EXAMPLE.COM ", TEST_PASSWORD,
                "NUTRICIONISTA", TipoDocumento.DNI, "12345678"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario saved = captor.getValue();
        assertEquals("ACTIVO", saved.getEstado());
        assertEquals("nuevo@example.com", saved.getCorreo());
        assertEquals("NUTRICIONISTA", saved.getRol().getNombre());
        assertNotEquals("nuevo@example.com", respuesta.contrasenaInicial());
        assertNotEquals("Ana", respuesta.contrasenaInicial());
        assertNotEquals("12345678", respuesta.contrasenaInicial());
        assertTrue(passwordEncoder.matches(respuesta.contrasenaInicial(), saved.getContrasenaHash()));
        assertTrue(saved.usaContrasenaInicial());
        assertEquals(42L, respuesta.usuario().idUsuario());
        assertEquals("NUTRICIONISTA", respuesta.usuario().rol());
    }

    @Test
    void rechazaCorreoDuplicado() {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@example.com"))
                .thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.crear(
                new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                        TEST_PASSWORD, "NUTRICIONISTA", TipoDocumento.DNI, "12345678")));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRolInexistente() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                        TEST_PASSWORD, "INEXISTENTE", TipoDocumento.DNI, "12345678")));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void creaUsuarioConRolEntrenadorExistente() throws Exception {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@example.com"))
                .thenReturn(false);
        when(rolRepository.findByNombreIgnoreCase("ENTRENADOR"))
                .thenReturn(Optional.of(entrenador));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(invocation -> {
                    Usuario saved = invocation.getArgument(0);
                    setId(saved, 43L);
                    return saved;
                });

        var respuesta = service.crear(new CrearUsuarioRequest(
                "Ana", "Prueba", "nuevo@example.com", TEST_PASSWORD, "ENTRENADOR",
                TipoDocumento.DNI, "12345678"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        assertEquals("ENTRENADOR", captor.getValue().getRol().getNombre());
        assertEquals("ENTRENADOR", respuesta.usuario().rol());
    }

    @Test
    void creaUsuarioConDocumentoNormalizado() throws Exception {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@example.com"))
                .thenReturn(false);
        when(rolRepository.findByNombreIgnoreCase("NUTRICIONISTA"))
                .thenReturn(Optional.of(nutricionista));
        when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var respuesta = service.crear(new CrearUsuarioRequest(
                "Ana", "Prueba", "nuevo@example.com", TEST_PASSWORD,
                "NUTRICIONISTA", TipoDocumento.CE, "123456789012"));

        assertEquals(TipoDocumento.CE, respuesta.usuario().tipoDocumento());
        assertEquals("123456789012", respuesta.usuario().numeroDocumento());
    }

    @Test
    void rechazaUsuarioNuevoSinDocumento() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(new CrearUsuarioRequest(
                "Ana", "Prueba", "nuevo@example.com", TEST_PASSWORD,
                "NUTRICIONISTA", null, null)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaFormatosDeDocumentoInvalidos() {
        for (var caso : java.util.List.of(
                new DocumentoCaso(TipoDocumento.DNI, "1234567"),
                new DocumentoCaso(TipoDocumento.DNI, "123456789"),
                new DocumentoCaso(TipoDocumento.DNI, "1234 678"),
                new DocumentoCaso(TipoDocumento.CE, ""),
                new DocumentoCaso(TipoDocumento.CE, "A-12"),
                new DocumentoCaso(TipoDocumento.CE, "12A"),
                new DocumentoCaso(TipoDocumento.CE, "AB 12"),
                new DocumentoCaso(TipoDocumento.CE, "ABCDEFGHIJKLM"))) {
            assertThrows(IllegalArgumentException.class, () -> service.crear(
                    new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                            TEST_PASSWORD, "NUTRICIONISTA", caso.tipo(), caso.numero())));
        }
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaDocumentoParcial() {
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                        TEST_PASSWORD, "NUTRICIONISTA", TipoDocumento.DNI, null)));
        assertThrows(IllegalArgumentException.class, () -> service.crear(
                new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                        TEST_PASSWORD, "NUTRICIONISTA", null, "12345678")));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaDocumentoDuplicadoPorTipoYNumero() {
        when(usuarioRepository.existsByCorreoIgnoreCase("nuevo@example.com"))
                .thenReturn(false);
        when(usuarioRepository.existsByTipoDocumentoAndNumeroDocumento(
                TipoDocumento.DNI, "12345678")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.crear(
                new CrearUsuarioRequest("Ana", "Prueba", "nuevo@example.com",
                        TEST_PASSWORD, "NUTRICIONISTA", TipoDocumento.DNI, "12345678")));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizaDatosSinAceptarCorreoDeOtraCuenta() throws Exception {
        Usuario existing = usuario(7L, recepcionista, "ACTIVO", "old@example.com");
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.existsByCorreoIgnoreCase("new@example.com"))
                .thenReturn(false);
        when(usuarioRepository.saveAndFlush(existing)).thenReturn(existing);

        var respuesta = service.actualizar(7L,
                new ActualizarUsuarioRequest("Nuevo", "Apellido", "new@example.com"));

        assertEquals("Nuevo", respuesta.nombres());
        assertEquals("new@example.com", respuesta.correo());
        verify(usuarioRepository).saveAndFlush(existing);
    }

    @Test
    void actualizacionAntiguaConservaDocumentoExistente() throws Exception {
        Usuario existing = usuario(7L, recepcionista, "ACTIVO", "old@example.com");
        existing.setTipoDocumento(TipoDocumento.CE);
        existing.setNumeroDocumento("AB123");
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.existsByCorreoIgnoreCase("new@example.com"))
                .thenReturn(false);
        when(usuarioRepository.saveAndFlush(existing)).thenReturn(existing);

        service.actualizar(7L,
                new ActualizarUsuarioRequest("Nuevo", "Apellido", "new@example.com"));

        assertEquals(TipoDocumento.CE, existing.getTipoDocumento());
        assertEquals("AB123", existing.getNumeroDocumento());
    }

    @Test
    void actualizacionPuedeLimpiarDocumentoEnviandoAmbosNull() throws Exception {
        Usuario existing = usuario(7L, recepcionista, "ACTIVO", "old@example.com");
        existing.setTipoDocumento(TipoDocumento.DNI);
        existing.setNumeroDocumento("12345678");
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.existsByCorreoIgnoreCase("new@example.com"))
                .thenReturn(false);
        when(usuarioRepository.saveAndFlush(existing)).thenReturn(existing);

        service.actualizar(7L, new ActualizarUsuarioRequest(
                "Nuevo", "Apellido", "new@example.com", null, null));

        assertNull(existing.getTipoDocumento());
        assertNull(existing.getNumeroDocumento());
    }

    @Test
    void rechazaCorreoDuplicadoDuranteActualizacion() throws Exception {
        Usuario existing = usuario(7L, recepcionista, "ACTIVO", "old@example.com");
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(existing));
        when(usuarioRepository.existsByCorreoIgnoreCase("occupied@example.com"))
                .thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.actualizar(7L,
                new ActualizarUsuarioRequest(
                        "Nuevo", "Apellido", "occupied@example.com")));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void noPermiteCambiarElRolDelUltimoAdministradorActivo() throws Exception {
        Usuario admin = usuario(1L, administrador, "ACTIVO", "admin@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(1L)).thenReturn(Optional.of(admin));
        when(rolRepository.findByNombreIgnoreCase("RECEPCIONISTA"))
                .thenReturn(Optional.of(recepcionista));
        when(usuarioRepository.countByRoleIdAndEstadoIgnoreCase(1L, "ACTIVO"))
                .thenReturn(1L);

        assertThrows(ConflictoException.class,
                () -> service.cambiarRol(1L, "RECEPCIONISTA"));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaRolInexistenteAlCambiarRol() throws Exception {
        Usuario user = usuario(7L, recepcionista, "ACTIVO", "user@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(user));
        when(rolRepository.findByNombreIgnoreCase("NO_EXISTE"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.cambiarRol(7L, "NO_EXISTE"));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void permiteCambiarAEntrenador() throws Exception {
        Usuario user = usuario(7L, recepcionista, "ACTIVO", "user@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(7L)).thenReturn(Optional.of(user));
        when(rolRepository.findByNombreIgnoreCase("ENTRENADOR"))
                .thenReturn(Optional.of(entrenador));
        when(usuarioRepository.saveAndFlush(user)).thenReturn(user);

        assertEquals("ENTRENADOR", service.cambiarRol(7L, "ENTRENADOR").rol());
        assertEquals("ENTRENADOR", user.getRol().getNombre());
    }

    @Test
    void permiteCambiarRolSiQuedaOtroAdministradorActivo() throws Exception {
        Usuario admin = usuario(1L, administrador, "ACTIVO", "admin@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(1L)).thenReturn(Optional.of(admin));
        when(rolRepository.findByNombreIgnoreCase("RECEPCIONISTA"))
                .thenReturn(Optional.of(recepcionista));
        when(usuarioRepository.countByRoleIdAndEstadoIgnoreCase(1L, "ACTIVO"))
                .thenReturn(2L);
        when(usuarioRepository.saveAndFlush(admin)).thenReturn(admin);

        assertEquals("RECEPCIONISTA", service.cambiarRol(1L, "RECEPCIONISTA").rol());
    }

    @Test
    void noPermiteDesactivarAlUltimoAdministradorActivo() throws Exception {
        Usuario admin = usuario(1L, administrador, "ACTIVO", "admin@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleIdAndEstadoIgnoreCase(1L, "ACTIVO"))
                .thenReturn(1L);

        assertThrows(ConflictoException.class, () -> service.desactivar(1L));
        assertEquals("ACTIVO", admin.getEstado());
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void permiteDesactivarSiQuedaOtroAdministradorActivo() throws Exception {
        Usuario admin = usuario(1L, administrador, "ACTIVO", "admin@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(1L)).thenReturn(Optional.of(admin));
        when(usuarioRepository.countByRoleIdAndEstadoIgnoreCase(1L, "ACTIVO"))
                .thenReturn(2L);
        when(usuarioRepository.saveAndFlush(admin)).thenReturn(admin);

        assertEquals("INACTIVO", service.desactivar(1L).estado());
        verify(refreshSessions).revocarUsuario(1L);
    }

    @Test
    void activaUsuarioInactivo() throws Exception {
        Usuario user = usuario(9L, recepcionista, "INACTIVO", "user@example.com");
        when(rolRepository.findLockedByNombreIgnoreCase("ADMINISTRADOR"))
                .thenReturn(Optional.of(administrador));
        when(usuarioRepository.findLockedById(9L)).thenReturn(Optional.of(user));
        when(usuarioRepository.saveAndFlush(user)).thenReturn(user);

        assertEquals("ACTIVO", service.activar(9L).estado());
    }

    @Test
    void cambiaContrasenaAlmacenandoSoloElHash() throws Exception {
        Usuario user = usuario(9L, recepcionista, "ACTIVO", "user@example.com");
        when(usuarioRepository.findLockedById(9L)).thenReturn(Optional.of(user));
        when(usuarioRepository.saveAndFlush(user)).thenReturn(user);

        service.cambiarContrasena(9L,
                new CambiarContrasenaUsuarioRequest(TEST_PASSWORD));

        assertNotEquals(TEST_PASSWORD, user.getContrasenaHash());
        assertTrue(passwordEncoder.matches(TEST_PASSWORD, user.getContrasenaHash()));
        verify(refreshSessions).revocarUsuario(9L);
    }

    @Test
    void devuelve404ParaUsuarioInexistente() {
        when(usuarioRepository.findByIdUsuario(88L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.buscarPorId(88L));
    }

    @Test
    void listaUsuariosMapeandoElRolSinDevolverLaEntidad() throws Exception {
        Usuario user = usuario(9L, recepcionista, "ACTIVO", "user@example.com");
        when(usuarioRepository.findAllByOrderByIdUsuarioAsc())
                .thenReturn(java.util.List.of(user));

        var respuestas = service.listarTodos();

        assertEquals(1, respuestas.size());
        assertEquals("RECEPCIONISTA", respuestas.getFirst().rol());
        assertEquals("user@example.com", respuestas.getFirst().correo());
    }

    @Test
    void devuelveUsuarioExistentePorId() throws Exception {
        Usuario user = usuario(9L, nutricionista, "ACTIVO", "user@example.com");
        when(usuarioRepository.findByIdUsuario(9L)).thenReturn(Optional.of(user));

        var respuesta = service.buscarPorId(9L);

        assertEquals(9L, respuesta.idUsuario());
        assertEquals("NUTRICIONISTA", respuesta.rol());
    }

    private static Rol rol(Long id, String nombre) throws Exception {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        Field field = Rol.class.getDeclaredField("idRol");
        field.setAccessible(true);
        field.set(rol, id);
        return rol;
    }

    private static Usuario usuario(
            Long id, Rol rol, String estado, String correo) throws Exception {
        Usuario user = new Usuario();
        user.setRol(rol);
        user.setNombres("Nombre");
        user.setApellidos("Apellido");
        user.setCorreo(correo);
        user.setContrasenaHash("no-es-un-hash-real-de-produccion");
        user.setEstado(estado);
        Field field = Usuario.class.getDeclaredField("idUsuario");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    private static void setId(Usuario user, Long id) throws Exception {
        Field field = Usuario.class.getDeclaredField("idUsuario");
        field.setAccessible(true);
        field.set(user, id);
    }

    private record DocumentoCaso(TipoDocumento tipo, String numero) {
    }
}
