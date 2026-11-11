package com.gimnasio.sistemagym.service;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gimnasio.sistemagym.entity.Rol;
import com.gimnasio.sistemagym.entity.SesionRefresh;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.repository.SesionRefreshRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {
    @Mock private SesionRefreshRepository sessions;
    @Mock private UsuarioRepository users;
    @Mock private JwtTokenService jwt;
    @Mock private CustomUserDetailsService userDetails;
    private RefreshSessionService service;
    private Usuario user;
    private org.springframework.security.core.userdetails.UserDetails principal;

    @BeforeEach
    void setUp() {
        service = new RefreshSessionService(sessions, users, jwt, userDetails);
        user = new Usuario();
        user.setCorreo("trainer@example.test");
        user.setContrasenaHash("unused");
        user.setRol(new Rol());
        user.getRol().setNombre("ENTRENADOR");
        principal = User.withUsername(user.getCorreo()).password("unused").roles("ENTRENADOR").build();
        when(userDetails.loadUserByUsername(user.getCorreo())).thenReturn(principal);
        when(jwt.generar(org.mockito.ArgumentMatchers.eq(principal),
                org.mockito.ArgumentMatchers.eq(0L), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("short-access-jwt");
        when(jwt.getExpirationMs()).thenReturn(900_000L);
    }

    @Test
    void soloPersisteHashesYLaRotacionRechazaElRefreshAnterior() throws Exception {
        when(sessions.save(any(SesionRefresh.class))).thenAnswer(call -> call.getArgument(0));
        ArgumentCaptor<SesionRefresh> stored = ArgumentCaptor.forClass(SesionRefresh.class);
        var issued = service.crear(user);
        verify(sessions).save(stored.capture());
        SesionRefresh row = stored.getValue();
        assertTrue(issued.refreshLifetime().equals(java.time.Duration.ofHours(12)));
        assertNotEquals(issued.refreshToken(), row.getTokenHash());
        assertNotEquals(issued.csrfToken(), row.getCsrfHash());
        assertTrue(row.getTokenHash().matches("[a-f0-9]{64}"));
        assertTrue(row.getCsrfHash().matches("[a-f0-9]{64}"));

        when(sessions.buscarBloqueadaPorHash(digest(issued.refreshToken())))
                .thenReturn(Optional.of(row));
        when(users.findCredentialVersionByCorreo(user.getCorreo())).thenReturn(Optional.of(0L));
        when(sessions.saveAndFlush(row)).thenReturn(row);
        var rotated = service.renovar(issued.refreshToken(), issued.csrfToken());
        assertNotEquals(issued.refreshToken(), rotated.refreshToken());
        assertNotEquals(issued.csrfToken(), rotated.csrfToken());
        assertNotEquals(digest(issued.refreshToken()), row.getTokenHash());

        when(sessions.buscarBloqueadaPorHash(digest(issued.refreshToken())))
                .thenReturn(Optional.empty());
        assertThrows(RefreshSessionService.SesionInvalidaException.class,
                () -> service.renovar(issued.refreshToken(), issued.csrfToken()));
    }

    @Test
    void csrfIncorrectoNoRotaLaSesion() {
        when(sessions.save(any(SesionRefresh.class))).thenAnswer(call -> call.getArgument(0));
        var issued = service.crear(user);
        when(sessions.buscarBloqueadaPorHash(digest(issued.refreshToken())))
                .thenAnswer(call -> {
                    SesionRefresh row = new SesionRefresh("id", user,
                            digest(issued.refreshToken()), digest(issued.csrfToken()), 0,
                            Instant.now(), Instant.now().plusSeconds(3600));
                    return Optional.of(row);
                });
        assertThrows(RefreshSessionService.SesionInvalidaException.class,
                () -> service.renovar(issued.refreshToken(), "attacker-csrf"));
    }

    @Test
    void logoutRevocaSoloLaSesionConCsrfValido() {
        when(sessions.save(any(SesionRefresh.class))).thenAnswer(call -> call.getArgument(0));
        var issued = service.crear(user);
        ArgumentCaptor<SesionRefresh> stored = ArgumentCaptor.forClass(SesionRefresh.class);
        org.mockito.Mockito.verify(sessions).save(stored.capture());
        SesionRefresh row = stored.getValue();
        when(sessions.buscarBloqueadaPorHash(digest(issued.refreshToken())))
                .thenReturn(Optional.of(row));

        service.revocar(issued.refreshToken(), "incorrect-csrf");
        org.mockito.Mockito.verify(sessions, org.mockito.Mockito.never()).delete(any(SesionRefresh.class));

        service.revocar(issued.refreshToken(), issued.csrfToken());
        org.mockito.Mockito.verify(sessions).delete(row);
    }

    @Test
    void rechazaRefreshCuandoCambioLaVersionDeCredenciales() {
        when(sessions.save(any(SesionRefresh.class))).thenAnswer(call -> call.getArgument(0));
        var issued = service.crear(user);
        ArgumentCaptor<SesionRefresh> stored = ArgumentCaptor.forClass(SesionRefresh.class);
        org.mockito.Mockito.verify(sessions).save(stored.capture());
        when(sessions.buscarBloqueadaPorHash(digest(issued.refreshToken())))
                .thenReturn(Optional.of(stored.getValue()));
        when(users.findCredentialVersionByCorreo(user.getCorreo())).thenReturn(Optional.of(1L));

        assertThrows(RefreshSessionService.SesionInvalidaException.class,
                () -> service.renovar(issued.refreshToken(), issued.csrfToken()));
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
