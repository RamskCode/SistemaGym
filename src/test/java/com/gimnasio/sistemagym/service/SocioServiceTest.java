package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarSocioRequest;
import com.gimnasio.sistemagym.entity.Auditoria;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.AuditoriaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SocioServiceTest {

    @Mock
    private SocioRepository socioRepository;

    @Mock
    private GeneradorCodigoSocio generadorCodigoSocio;

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private SocioService service;

    @BeforeEach
    void setUp() {
        service = new SocioService(socioRepository, generadorCodigoSocio,
                auditoriaRepository, usuarioRepository);
    }

    @Test
    void registraSocioConCodigoEstadoYRespuestaDto() throws Exception {
        when(generadorCodigoSocio.crearConSiguienteCodigo(any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<String, Socio> registrar =
                            invocation.getArgument(0);
                    return registrar.apply("SOC-0009");
                });
        when(socioRepository.saveAndFlush(any(Socio.class))).thenAnswer(invocation -> {
            Socio saved = invocation.getArgument(0);
            setId(saved, 32L);
            return saved;
        });

        Socio nuevo = new Socio();
        nuevo.setNombres("Ana");
        nuevo.setApellidos("Prueba");
        var respuesta = service.registrar(nuevo);

        assertEquals(32L, respuesta.idSocio());
        assertEquals("SOC-0009", respuesta.codigoSocio());
        assertEquals("ACTIVO", respuesta.estado());
        assertEquals("Ana", respuesta.nombres());
        verify(generadorCodigoSocio).crearConSiguienteCodigo(any());
        verify(socioRepository).saveAndFlush(nuevo);
    }

    @Test
    void listaDtosYConsultaPorIdSinExponerEntidad() throws Exception {
        Socio socio = socio(5L, "SOC-0005", "ACTIVO");
        when(socioRepository.findAll()).thenReturn(List.of(socio));
        when(socioRepository.findById(5L)).thenReturn(Optional.of(socio));

        var lista = service.listarTodos();
        var detalle = service.buscarPorId(5L);

        assertEquals("SOC-0005", lista.getFirst().codigoSocio());
        assertEquals(5L, detalle.idSocio());
        assertEquals("ACTIVO", detalle.estado());
        assertEquals(12, detalle.getClass().getRecordComponents().length);
    }

    @Test
    void actualizarSoloCambiaCamposPermitidos() throws Exception {
        Socio socio = socio(5L, "SOC-0005", "INACTIVO");
        LocalDateTime fechaRegistroOriginal = socio.getFechaRegistro();
        when(socioRepository.findById(5L)).thenReturn(Optional.of(socio));
        when(socioRepository.findByDni("12345678")).thenReturn(Optional.empty());
        when(socioRepository.save(socio)).thenReturn(socio);

        var respuesta = service.actualizar(5L,
                new ActualizarSocioRequest("12345678", "DNI", "12345678", "Nombre nuevo", "Apellido",
                        null, null, null));

        assertEquals("Nombre nuevo", socio.getNombres());
        assertEquals("12345678", socio.getDni());
        assertEquals("SOC-0005", socio.getCodigoSocio());
        assertEquals("INACTIVO", socio.getEstado());
        assertEquals(fechaRegistroOriginal, socio.getFechaRegistro());
        assertEquals("SOC-0005", respuesta.codigoSocio());
        verify(socioRepository).save(socio);
    }

    @Test
    void socioInexistenteSePropagaComoNotFound() {
        when(socioRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.buscarPorId(404L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(404L,
                        new ActualizarSocioRequest(null, null, null, "Ana", "Prueba",
                                null, null, null)));
    }

    @Test
    void dniDeOtroSocioEsConflictoYNoGuarda() throws Exception {
        Socio actual = socio(5L, "SOC-0005", "ACTIVO");
        Socio otro = socio(6L, "SOC-0006", "ACTIVO");
        when(socioRepository.findById(5L)).thenReturn(Optional.of(actual));
        when(socioRepository.findByDni("87654321")).thenReturn(Optional.of(otro));

        assertThrows(ConflictoException.class,
                () -> service.actualizar(5L,
                        new ActualizarSocioRequest("87654321", "DNI", "87654321", "Ana", "Prueba",
                                null, null, null)));
        verify(socioRepository, never()).save(any());
    }

    @Test
    void rechazaDocumentoFueraDeFormatoYPermiteDocumentoOpcional() {
        Socio invalido = new Socio();
        invalido.setTipoDocumento("CE");
        invalido.setNumeroDocumento("1234567890123");
        assertThrows(IllegalArgumentException.class, () -> service.registrar(invalido));
        verifyNoInteractions(generadorCodigoSocio, socioRepository);
    }

    @Test
    void aceptaCeNumericoDentroDelLimiteYLoNormaliza() throws Exception {
        when(generadorCodigoSocio.crearConSiguienteCodigo(any())).thenAnswer(invocation -> {
            java.util.function.Function<String, Socio> registrar = invocation.getArgument(0);
            return registrar.apply("SOC-0009");
        });
        when(socioRepository.saveAndFlush(any(Socio.class))).thenAnswer(invocation -> {
            Socio saved = invocation.getArgument(0); setId(saved, 32L); return saved;
        });
        Socio nuevo = new Socio();
        nuevo.setTipoDocumento(" ce ");
        nuevo.setNumeroDocumento(" 12345 ");
        nuevo.setNombres("Eva"); nuevo.setApellidos("Prueba");

        var respuesta = service.registrar(nuevo);
        assertEquals("CE", respuesta.tipoDocumento());
        assertEquals("12345", respuesta.numeroDocumento());
        assertNull(nuevo.getDni());
    }

    @Test
    void errorDeUnicidadAlGuardarSeConvierteEnConflictoSinResultado() {
        when(generadorCodigoSocio.crearConSiguienteCodigo(any()))
                .thenAnswer(invocation -> {
                    java.util.function.Function<String, Socio> registrar =
                            invocation.getArgument(0);
                    return registrar.apply("SOC-0001");
                });
        when(socioRepository.saveAndFlush(any(Socio.class)))
                .thenThrow(new DataIntegrityViolationException("detalle interno"));
        Socio nuevo = new Socio();
        nuevo.setNombres("Ana");
        nuevo.setApellidos("Prueba");

        assertThrows(ConflictoException.class, () -> service.registrar(nuevo));
        verify(socioRepository).saveAndFlush(nuevo);
    }

    @Test
    void desactivaRegistraAuditoriaConElUsuarioAutenticadoYTransicion() throws Exception {
        Socio socio = socio(12L, "SOC-0012", "ACTIVO");
        Usuario usuario = new Usuario();
        setUsuarioId(usuario, 44L);
        when(socioRepository.findLockedById(12L)).thenReturn(Optional.of(socio));
        when(socioRepository.saveAndFlush(socio)).thenReturn(socio);
        when(usuarioRepository.findByCorreo("recepcionista@example.invalid"))
                .thenReturn(Optional.of(usuario));

        var respuesta = service.desactivar(12L, "Solicitud del socio",
                "recepcionista@example.invalid");

        assertEquals("INACTIVO", respuesta.estado());
        var captor = org.mockito.ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        Auditoria auditoria = captor.getValue();
        assertEquals(44L, auditoria.getIdUsuario());
        assertEquals("DESACTIVAR_SOCIO", auditoria.getAccion());
        assertEquals("SOCIO", auditoria.getEntidad());
        assertEquals("12", auditoria.getIdRegistro());
        assertEquals("Solicitud del socio", auditoria.getDetalle().get("motivo"));
        assertEquals("ACTIVO", auditoria.getDetalle().get("estadoAnterior"));
        assertEquals("INACTIVO", auditoria.getDetalle().get("estadoNuevo"));
        verify(socioRepository).findLockedById(12L);
    }

    @Test
    void activaSocioInactivoYRegistraLaTransicionCorrecta() throws Exception {
        Socio socio = socio(12L, "SOC-0012", "INACTIVO");
        Usuario usuario = new Usuario();
        setUsuarioId(usuario, 45L);
        when(socioRepository.findLockedById(12L)).thenReturn(Optional.of(socio));
        when(socioRepository.saveAndFlush(socio)).thenReturn(socio);
        when(usuarioRepository.findByCorreo("admin@example.invalid"))
                .thenReturn(Optional.of(usuario));

        var respuesta = service.activar(12L, "Regularización", "admin@example.invalid");

        assertEquals("ACTIVO", respuesta.estado());
        var captor = org.mockito.ArgumentCaptor.forClass(Auditoria.class);
        verify(auditoriaRepository).saveAndFlush(captor.capture());
        assertEquals("ACTIVAR_SOCIO", captor.getValue().getAccion());
        assertEquals("INACTIVO", captor.getValue().getDetalle().get("estadoAnterior"));
        assertEquals("ACTIVO", captor.getValue().getDetalle().get("estadoNuevo"));
    }

    @Test
    void rechazaMotivoVacioYSocioInexistenteSinEscribirAuditoria() {
        assertThrows(IllegalArgumentException.class,
                () -> service.activar(12L, "   ", "admin@example.invalid"));
        when(socioRepository.findLockedById(404L)).thenReturn(Optional.empty());
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.desactivar(404L, "Motivo", "admin@example.invalid"));
        verifyNoInteractions(auditoriaRepository);
    }

    @Test
    void transicionRedundanteDevuelveConflictoSinAuditar() throws Exception {
        Socio activo = socio(12L, "SOC-0012", "ACTIVO");
        when(socioRepository.findLockedById(12L)).thenReturn(Optional.of(activo));

        assertThrows(ConflictoException.class,
                () -> service.activar(12L, "Motivo", "admin@example.invalid"));

        verifyNoInteractions(auditoriaRepository, usuarioRepository);
        verify(socioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrosConcurrentesRecibenCodigosDistintosConGeneradorSerializado()
            throws Exception {
        GeneradorCodigoSocio generadorSerializado = new GeneradorCodigoSocio() {
            private int siguiente = 1;

            @Override
            public synchronized Socio crearConSiguienteCodigo(
                    java.util.function.Function<String, Socio> crearSocio) {
                String codigo = String.format("SOC-%04d", siguiente++);
                return crearSocio.apply(codigo);
            }
        };
        SocioService servicioConcurrente = new SocioService(
                socioRepository, generadorSerializado,
                auditoriaRepository, usuarioRepository);
        java.util.concurrent.atomic.AtomicLong idSiguiente =
                new java.util.concurrent.atomic.AtomicLong(1L);
        when(socioRepository.saveAndFlush(any(Socio.class))).thenAnswer(invocation -> {
            Socio socio = invocation.getArgument(0);
            setId(socio, idSiguiente.getAndIncrement());
            return socio;
        });
        java.util.concurrent.ExecutorService ejecutor =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var primero = ejecutor.submit(() -> servicioConcurrente.registrar(socioNuevo()));
            var segundo = ejecutor.submit(() -> servicioConcurrente.registrar(socioNuevo()));

            var respuestas = List.of(primero.get(), segundo.get());
            assertEquals(2, respuestas.stream().map(r -> r.codigoSocio()).distinct().count());
            assertTrue(respuestas.stream().anyMatch(r -> r.codigoSocio().equals("SOC-0001")));
            assertTrue(respuestas.stream().anyMatch(r -> r.codigoSocio().equals("SOC-0002")));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private Socio socioNuevo() {
        Socio socio = new Socio();
        socio.setNombres("Nombre");
        socio.setApellidos("Apellido");
        return socio;
    }

    @Test
    void recortaBusquedaGlobalYMapeaResultadosADto() throws Exception {
        Socio socio = socio(5L, "SOC-0005", "ACTIVO");
        when(socioRepository.buscarGlobal("Carlos Rojas"))
                .thenReturn(List.of(socio));

        var resultados = service.buscarGlobal("  Carlos Rojas  ");

        assertEquals(1, resultados.size());
        assertEquals("SOC-0005", resultados.getFirst().codigoSocio());
        verify(socioRepository).buscarGlobal("Carlos Rojas");
    }

    @Test
    void rechazaBusquedaGlobalVaciaOSoloConEspacios() {
        assertThrows(IllegalArgumentException.class,
                () -> service.buscarGlobal(""));
        assertThrows(IllegalArgumentException.class,
                () -> service.buscarGlobal("   "));
        verify(socioRepository, never()).buscarGlobal(any());
    }

    @Test
    void estadoSoloAceptaActivoEInactivoYDevuelveDtos() throws Exception {
        Socio activo = socio(5L, "SOC-0005", "ACTIVO");
        Socio inactivo = socio(6L, "SOC-0006", "INACTIVO");
        when(socioRepository.findByEstado("ACTIVO")).thenReturn(List.of(activo));
        when(socioRepository.findByEstado("INACTIVO")).thenReturn(List.of(inactivo));

        assertEquals("ACTIVO", service.listarPorEstado(" activo ")
                .getFirst().estado());
        assertEquals("INACTIVO", service.listarPorEstado("INACTIVO")
                .getFirst().estado());
        assertThrows(IllegalArgumentException.class,
                () -> service.listarPorEstado("PENDIENTE"));
    }

    private static Socio socio(Long id, String codigo, String estado) throws Exception {
        Socio socio = new Socio();
        setId(socio, id);
        socio.setCodigoSocio(codigo);
        socio.setDni("1000000" + id);
        socio.setNombres("Nombre");
        socio.setApellidos("Apellido");
        socio.setEstado(estado);
        setField(socio, "fechaRegistro", LocalDateTime.of(2024, 1, 2, 3, 4));
        return socio;
    }

    private static void setId(Socio socio, Long id) throws Exception {
        setField(socio, "idSocio", id);
    }

    private static void setField(Socio socio, String name, Object value) throws Exception {
        Field field = Socio.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(socio, value);
    }

    private static void setUsuarioId(Usuario usuario, Long id) throws Exception {
        Field field = Usuario.class.getDeclaredField("idUsuario");
        field.setAccessible(true);
        field.set(usuario, id);
    }
}
