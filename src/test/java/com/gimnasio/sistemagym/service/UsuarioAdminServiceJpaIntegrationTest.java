package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-users-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Import({UsuarioAdminService.class, PasswordConfig.class, PasswordPolicy.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UsuarioAdminServiceJpaIntegrationTest {

    @Autowired
    private UsuarioAdminService service;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RolRepository rolRepository;

    private Rol rolAdministrador;
    private Rol rolNutricionista;
    private Usuario ultimoAdministrador;

    @BeforeEach
    void prepararBaseAislada() {
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();

        rolAdministrador = guardarRol("ADMINISTRADOR");
        rolNutricionista = guardarRol("NUTRICIONISTA");
        ultimoAdministrador = usuario("admin-test@example.invalid", rolAdministrador);
        ultimoAdministrador = usuarioRepository.saveAndFlush(ultimoAdministrador);
    }

    @AfterEach
    void limpiarBaseAislada() {
        usuarioRepository.deleteAllInBatch();
        rolRepository.deleteAllInBatch();
    }

    @Test
    void noDesactivaAlUltimoAdministradorActivoYConservaLaFila() {
        assertThrows(ConflictoException.class,
                () -> service.desactivar(ultimoAdministrador.getIdUsuario()));

        Usuario persistido = usuarioRepository.findByIdUsuario(
                ultimoAdministrador.getIdUsuario()).orElseThrow();
        assertEquals("ACTIVO", persistido.getEstado());
        assertEquals(1L, usuarioRepository.countByRoleIdAndEstadoIgnoreCase(
                rolAdministrador.getIdRol(), "ACTIVO"));
    }

    @Test
    void noDegradaAlUltimoAdministradorActivoYConservaSuRol() {
        assertThrows(ConflictoException.class,
                () -> service.cambiarRol(
                        ultimoAdministrador.getIdUsuario(), "NUTRICIONISTA"));

        Usuario persistido = usuarioRepository.findByIdUsuario(
                ultimoAdministrador.getIdUsuario()).orElseThrow();
        assertEquals("ADMINISTRADOR", persistido.getRol().getNombre());
        assertEquals(1L, usuarioRepository.countByRoleIdAndEstadoIgnoreCase(
                rolAdministrador.getIdRol(), "ACTIVO"));
    }

    @Test
    void confirmaEscrituraTransaccionalCuandoPermaneceOtroAdministrador() {
        Usuario segundoAdministrador = usuarioRepository.saveAndFlush(
                usuario("admin-segundo@example.invalid", rolAdministrador));

        service.desactivar(ultimoAdministrador.getIdUsuario());

        assertEquals("INACTIVO", usuarioRepository.findByIdUsuario(
                ultimoAdministrador.getIdUsuario()).orElseThrow().getEstado());
        assertEquals("ACTIVO", usuarioRepository.findByIdUsuario(
                segundoAdministrador.getIdUsuario()).orElseThrow().getEstado());
        assertEquals(1L, usuarioRepository.countByRoleIdAndEstadoIgnoreCase(
                rolAdministrador.getIdRol(), "ACTIVO"));
    }

    @Test
    void solicitudesConcurrentesNoEliminanAlUltimoAdministradorActivo()
            throws Exception {
        Usuario segundoAdministrador = usuarioRepository.saveAndFlush(
                usuario("admin-concurrente@example.invalid", rolAdministrador));
        CountDownLatch inicio = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> primera = executor.submit(() -> {
                inicio.await();
                return intentarDesactivar(ultimoAdministrador.getIdUsuario());
            });
            Future<Boolean> segunda = executor.submit(() -> {
                inicio.await();
                return intentarDesactivar(segundoAdministrador.getIdUsuario());
            });
            inicio.countDown();

            boolean primeraDesactivada = primera.get(10, TimeUnit.SECONDS);
            boolean segundaDesactivada = segunda.get(10, TimeUnit.SECONDS);
            assertNotEquals(primeraDesactivada, segundaDesactivada);
            assertEquals(1L, usuarioRepository.countByRoleIdAndEstadoIgnoreCase(
                    rolAdministrador.getIdRol(), "ACTIVO"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void documentosNullMantienenCompatibilidadConUsuariosExistentes() {
        Usuario existente = usuario("sin-documento@example.invalid", rolNutricionista);
        Usuario guardado = usuarioRepository.saveAndFlush(existente);

        Usuario recuperado = usuarioRepository.findByIdUsuario(guardado.getIdUsuario())
                .orElseThrow();
        assertEquals(null, recuperado.getTipoDocumento());
        assertEquals(null, recuperado.getNumeroDocumento());
    }

    @Test
    void aceptaMismoNumeroConTiposDeDocumentoDistintos() {
        service.crear(new CrearUsuarioRequest("DNI", "Usuario", "dni@example.invalid",
                "StrongTestPassword!42", "NUTRICIONISTA", TipoDocumento.DNI, "12345678"));
        service.crear(new CrearUsuarioRequest("CE", "Usuario", "ce@example.invalid",
                "StrongTestPassword!42", "NUTRICIONISTA", TipoDocumento.CE, "12345678"));

        assertEquals(2, usuarioRepository.findAll().stream()
                .filter(user -> "12345678".equals(user.getNumeroDocumento()))
                .count());
    }

    @Test
    void rechazaDuplicadoDeLaMismaCombinacionTipoYNumero() {
        service.crear(new CrearUsuarioRequest("Primero", "Usuario", "uno@example.invalid",
                "StrongTestPassword!42", "NUTRICIONISTA", TipoDocumento.DNI, "12345678"));

        assertThrows(ConflictoException.class, () -> service.crear(
                new CrearUsuarioRequest("Segundo", "Usuario", "dos@example.invalid",
                        "StrongTestPassword!42", "NUTRICIONISTA",
                        TipoDocumento.DNI, "12345678")));
    }

    private boolean intentarDesactivar(Long id) {
        try {
            service.desactivar(id);
            return true;
        } catch (ConflictoException ex) {
            return false;
        }
    }

    private Rol guardarRol(String nombre) {
        Rol rol = new Rol();
        rol.setNombre(nombre);
        return rolRepository.saveAndFlush(rol);
    }

    private Usuario usuario(String correo, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        usuario.setNombres("Usuario");
        usuario.setApellidos("De Prueba");
        usuario.setCorreo(correo);
        usuario.setContrasenaHash("placeholder-solo-para-pruebas");
        usuario.setEstado("ACTIVO");
        return usuario;
    }
}
