package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gimnasio.sistemagym.config.PasswordConfig;
import com.gimnasio.sistemagym.dto.CrearUsuarioRequest;
import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.repository.InvitacionUsuarioRepository;
import com.gimnasio.sistemagym.repository.RolRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:sistemagym-activation-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"})
@Import({UsuarioAdminService.class, UsuarioInvitacionService.class,
        PasswordConfig.class, PasswordPolicy.class, CustomUserDetailsService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UsuarioInvitacionServiceJpaTest {
    @Autowired UsuarioAdminService admin;
    @Autowired UsuarioInvitacionService invitations;
    @Autowired UsuarioRepository users;
    @Autowired RolRepository roles;
    @Autowired InvitacionUsuarioRepository tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomUserDetailsService details;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean InvitacionMailService mail;
    private final List<String> sentTokens = new ArrayList<>();

    @BeforeEach
    void setup() {
        tokens.deleteAllInBatch(); users.deleteAllInBatch(); roles.deleteAllInBatch(); sentTokens.clear();
        Rol role = new Rol(); role.setNombre("ENTRENADOR"); roles.saveAndFlush(role);
        doAnswer(call -> {
            sentTokens.add(call.getArgument(2)); return null;
        }).when(mail).send(anyString(), anyString(), anyString(), anyLong());
    }

    private Usuario create(String email) {
        Rol role = roles.findByNombreIgnoreCase("ENTRENADOR").orElseThrow();
        Usuario user = new Usuario();
        user.setRol(role);
        user.setNombres("Ana");
        user.setApellidos("Prueba");
        user.setCorreo(email);
        user.setContrasenaHash("pending-hash");
        user.setEstado("PENDIENTE");
        user.setTipoDocumento(TipoDocumento.DNI);
        user.setNumeroDocumento("12345678");
        return users.saveAndFlush(user);
    }

    @Test
    void creaPendienteEnvíaHashYActivaUnaSolaVezPermitiendoLoginSoloDespues() {
        Usuario user = create("activate@example.test");
        assertEquals("PENDIENTE", user.getEstado());
        assertThrows(UsernameNotFoundException.class,
                () -> details.loadUserByUsername(user.getCorreo()));

        var delivery = invitations.sendInvitation(user.getIdUsuario());
        assertTrue(delivery.sent());
        String raw = sentTokens.getFirst();
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(invitations.isTokenValid(raw));
        var saved = tokens.findAll().getFirst();
        assertNotEquals(raw, saved.getTokenHash());
        assertEquals(64, saved.getTokenHash().length());

        invitations.activate(raw, "StrongNewPassword!42");
        assertEquals("ACTIVO", users.findById(user.getIdUsuario()).orElseThrow().getEstado());
        assertEquals(user.getCorreo(), details.loadUserByUsername(user.getCorreo()).getUsername());
        assertFalse(invitations.isTokenValid(raw));
        assertThrows(IllegalArgumentException.class,
                () -> invitations.activate(raw, "AnotherStrongPassword!4"));
    }

    @Test
    void reenviarRevocaTokenAnteriorYFalloSmtpNoInvalidaLaPosibilidadDeReintentar() {
        Usuario user = create("retry@example.test");
        doAnswer(call -> { sentTokens.add(call.getArgument(2));
            throw new RuntimeException("simulated smtp failure");
        }).when(mail).send(anyString(), anyString(), anyString(), anyLong());
        var failed = invitations.sendInvitation(user.getIdUsuario());
        assertFalse(failed.sent());
        assertTrue(failed.message().contains("No se confirmó el envío"));
        assertEquals("PENDIENTE", users.findById(user.getIdUsuario()).orElseThrow().getEstado());
        assertEquals(1, tokens.count());

        reset(mail);
        doAnswer(call -> {
            sentTokens.add(call.getArgument(2)); return null;
        }).when(mail).send(anyString(), anyString(), anyString(), anyLong());
        var resent = invitations.sendInvitation(user.getIdUsuario());
        assertTrue(resent.sent());
        assertEquals(2, sentTokens.size());
        assertFalse(invitations.isTokenValid(sentTokens.getFirst()));
        assertTrue(invitations.isTokenValid(sentTokens.getLast()));
    }

    @Test
    void tokenCaducadoNoPuedeActivarYCuartoEnvioEn24HorasEsRechazado() {
        Usuario user = create("expired@example.test");
        invitations.sendInvitation(user.getIdUsuario());
        String expired = sentTokens.getFirst();
        jdbc.update("UPDATE invitaciones_usuario SET fecha_expiracion = DATEADD('HOUR', -1, CURRENT_TIMESTAMP) WHERE token_hash = ?",
                hash(expired));
        assertFalse(invitations.isTokenValid(expired));
        assertThrows(IllegalArgumentException.class,
                () -> invitations.activate(expired, "StrongNewPassword!42"));
        assertEquals("PENDIENTE", users.findById(user.getIdUsuario()).orElseThrow().getEstado());

        invitations.sendInvitation(user.getIdUsuario());
        invitations.sendInvitation(user.getIdUsuario());
        assertThrows(ConflictoException.class, () -> invitations.sendInvitation(user.getIdUsuario()));
    }

    @Test
    void administradorPuedeDarAccesoInmediatoAUnaCuentaPendientePreexistente() {
        Usuario pending = create("legacy-pending@example.test");
        var credentials = admin.restablecerAcceso(pending.getIdUsuario());
        Usuario activated = users.findById(pending.getIdUsuario()).orElseThrow();

        assertEquals("ACTIVO", activated.getEstado());
        assertTrue(activated.usaContrasenaInicial());
        assertTrue(passwordEncoder.matches(
                credentials.contrasenaInicial(), activated.getContrasenaHash()));
        assertEquals(activated.getCorreo(),
                details.loadUserByUsername(activated.getCorreo()).getUsername());
    }

    private static String hash(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
