package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.OperacionPagoIdempotenteRespuesta;
import com.gimnasio.sistemagym.entity.OperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.TipoOperacionPagoIdempotente;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoClaveIdempotenciaException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.exception.RechazoDeterministaOperacionException;
import com.gimnasio.sistemagym.repository.OperacionPagoIdempotenteRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.function.Function;
import com.gimnasio.sistemagym.entity.EstadoOperacionPagoIdempotente;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Owns reservation, owner-scoped lookup, and locked transactional finalization. */
@Service
public class OperacionPagoIdempotenteService {


    private final OperacionPagoIdempotenteRepository operacionRepository;
    private final UsuarioRepository usuarioRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OperacionPagoIdempotenteService(
            OperacionPagoIdempotenteRepository operacionRepository,
            UsuarioRepository usuarioRepository) {
        this.operacionRepository = operacionRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Executes the callback while holding the row lock and commits the serialized result atomically. */
    @Transactional
    public <T> T procesar(String correo, String idOperacion,
            TipoOperacionPagoIdempotente tipo, String hash, Class<T> clase,
            Supplier<T> ejecucion, Function<T, Long[]> referencias) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));
        OperacionPagoIdempotente operacion = operacionRepository
                .findLockedForOwner(idOperacion, usuario.getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación solicitada."));
        OperacionPagoIdempotenteRepository.CurrentOutcome outcome = operacionRepository
                .lockCurrentOutcomeForOwner(
                        idOperacion, usuario.getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación solicitada."));
        EstadoOperacionPagoIdempotente estadoActual = EstadoOperacionPagoIdempotente.valueOf(
                outcome.getEstadoActual());
        verificarIdentidad(operacion, tipo, hash);
        if (estadoActual == EstadoOperacionPagoIdempotente.CONFIRMED) {
            return leerResultado(outcome.getResultadoPayload(), clase);
        }
        if (estadoActual == EstadoOperacionPagoIdempotente.REJECTED) {
            lanzarRechazo(outcome.getRechazoTipo(), outcome.getRechazoMensaje());
        }
        // The native locking read above bypasses the first-level snapshot. Refresh
        // before mutation while retaining the row lock until this transaction commits.
        entityManager.refresh(operacion);
        T resultado;
        try {
            resultado = ejecucion.get();
        } catch (IllegalStateException ex) {
            throw new RechazoDeterministaOperacionException(ex);
        }
        try {
            Long[] ids = referencias.apply(resultado);
            operacion.confirmar(ids[0], ids[1], ids[2], serializar(resultado));
            operacionRepository.saveAndFlush(operacion);
            return resultado;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo guardar el resultado idempotente.", ex);
        }
    }

    /** Only business exceptions are recorded, after the business transaction has rolled back. */
    @Transactional
    public void rechazar(String correo, String idOperacion, String tipo, String mensaje) {
        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));
        OperacionPagoIdempotente operacion = operacionRepository
                .findLockedForOwner(idOperacion, usuario.getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación solicitada."));
        if (operacion.getEstado() == EstadoOperacionPagoIdempotente.PENDING) {
            operacion.rechazar(tipo, mensaje == null ? "La operación fue rechazada." : mensaje);
        }
    }

    private void verificarIdentidad(OperacionPagoIdempotente operacion,
            TipoOperacionPagoIdempotente tipo, String hash) {
        if (operacion.getTipoOperacion() != tipo || !MessageDigest.isEqual(
                operacion.getHashSolicitud().getBytes(StandardCharsets.US_ASCII),
                hash.getBytes(StandardCharsets.US_ASCII))) {
            throw new ConflictoClaveIdempotenciaException();
        }
    }

    private <T> T leerResultado(OperacionPagoIdempotente operacion, Class<T> clase) {
        return leerResultado(operacion.getResultadoPayload(), clase);
    }

    private <T> T leerResultado(String payload, Class<T> clase) {
        try (var input = new ObjectInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(payload)))) {
            return clase.cast(input.readObject());
        }
        catch (Exception ex) { throw new IllegalStateException("El resultado persistido no se puede recuperar.", ex); }
    }

    private String serializar(Object resultado) throws java.io.IOException {
        try (var bytes = new ByteArrayOutputStream(); var output = new ObjectOutputStream(bytes)) {
            output.writeObject(resultado);
            output.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    private void lanzarRechazo(OperacionPagoIdempotente operacion) {
        lanzarRechazo(operacion.getRechazoTipo(), operacion.getRechazoMensaje());
    }

    private void lanzarRechazo(String tipo, String mensaje) {
        switch (tipo) {
            case "CONFLICTO" -> throw new com.gimnasio.sistemagym.exception.ConflictoException(mensaje);
            case "NO_ENCONTRADO" -> throw new RecursoNoEncontradoException(mensaje);
            default -> throw new IllegalArgumentException(mensaje);
        }
    }

    /**
     * Creates one durable PENDING reservation. Concurrent inserts are resolved
     * by the database unique constraint; the loser reads and validates winner.
     * Repeating an existing PENDING reservation is the safe recovery behavior
     * if an earlier transaction is uncertain, the row lock serializes recovery.
     */
    public OperacionPagoIdempotenteRespuesta reservar(
            String correoPropietario,
            UUID clave,
            TipoOperacionPagoIdempotente tipo,
            String materialCanonicoSolicitud) {
        validarEntrada(correoPropietario, clave, tipo, materialCanonicoSolicitud);
        Usuario usuario = usuarioRepository.findByCorreo(correoPropietario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró el usuario autenticado."));
        String hash = sha256(materialCanonicoSolicitud);

        Optional<OperacionPagoIdempotente> existente = buscarPorClave(usuario, clave);
        if (existente.isPresent()) return validarMismaIdentidad(existente.get(), tipo, hash);

        try {
            OperacionPagoIdempotente creada = operacionRepository.saveAndFlush(
                    OperacionPagoIdempotente.reservar(usuario, clave, tipo, hash));
            return OperacionPagoIdempotenteRespuesta.desde(creada);
        } catch (DataIntegrityViolationException carrera) {
            // saveAndFlush runs in the repository transaction. Catch outside it,
            // then perform a fresh read instead of querying a rollback-only transaction.
            OperacionPagoIdempotente ganadora = buscarPorClave(usuario, clave)
                    .orElseThrow(() -> carrera);
            return validarMismaIdentidad(ganadora, tipo, hash);
        }
    }

    /** Owner-scoped lookup: an unknown ID and another user's ID are indistinguishable. */
    public OperacionPagoIdempotenteRespuesta consultar(String correoPropietario, String idOperacion) {
        if (correoPropietario == null || correoPropietario.isBlank()
                || idOperacion == null || idOperacion.isBlank()) {
            throw new RecursoNoEncontradoException("No se encontró la operación solicitada.");
        }
        Usuario usuario = usuarioRepository.findByCorreo(correoPropietario)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la operación solicitada."));
        return operacionRepository.findByIdOperacionAndUsuario_IdUsuario(
                        idOperacion, usuario.getIdUsuario())
                .map(OperacionPagoIdempotenteRespuesta::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontró la operación solicitada."));
    }

    /** Recovers an operation by its idempotency key, scoped to the authenticated owner. */
    @Transactional(readOnly = true)
    public OperacionPagoIdempotenteRespuesta consultarPorClave(String correoPropietario, String clave) {
        UUID claveUuid;
        try {
            claveUuid = UUID.fromString(clave);
            if (!claveUuid.toString().equalsIgnoreCase(clave)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Idempotency-Key debe tener formato UUID válido.");
        }
        if (correoPropietario == null || correoPropietario.isBlank()) {
            throw new RecursoNoEncontradoException("No se encontró la operación solicitada.");
        }
        Usuario usuario = usuarioRepository.findByCorreo(correoPropietario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación solicitada."));
        OperacionPagoIdempotente operacion = operacionRepository
                .findByClaveIdempotenciaAndUsuario_IdUsuario(claveUuid.toString(), usuario.getIdUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la operación solicitada."));
        Object resultado = null;
        if (operacion.getEstado() == com.gimnasio.sistemagym.entity.EstadoOperacionPagoIdempotente.CONFIRMED) {
            Class<?> tipo = operacion.getTipoOperacion() == TipoOperacionPagoIdempotente.MEMBRESIA_INICIAL
                    ? com.gimnasio.sistemagym.dto.RegistroMembresiaRespuesta.class
                    : com.gimnasio.sistemagym.dto.PagoPosteriorMembresiaRespuesta.class;
            resultado = leerResultado(operacion, tipo);
        }
        var respuesta = OperacionPagoIdempotenteRespuesta.desde(operacion);
        return new OperacionPagoIdempotenteRespuesta(respuesta.idOperacion(), respuesta.tipoOperacion(),
                respuesta.estado(), respuesta.fechaCreacion(), respuesta.fechaActualizacion(),
                respuesta.idMembresia(), respuesta.idPago(), respuesta.idAplicacion(),
                respuesta.rechazoTipo(), respuesta.rechazoMensaje(), resultado);
    }

    private Optional<OperacionPagoIdempotente> buscarPorClave(Usuario usuario, UUID clave) {
        return operacionRepository.findByUsuario_IdUsuarioAndClaveIdempotencia(
                usuario.getIdUsuario(), clave.toString());
    }

    private OperacionPagoIdempotenteRespuesta validarMismaIdentidad(
            OperacionPagoIdempotente existente,
            TipoOperacionPagoIdempotente tipo,
            String hash) {
        verificarIdentidad(existente, tipo, hash);
        return OperacionPagoIdempotenteRespuesta.desde(existente);
    }

    private void validarEntrada(String correo, UUID clave,
            TipoOperacionPagoIdempotente tipo, String material) {
        if (correo == null || correo.isBlank() || clave == null || tipo == null
                || material == null || material.isBlank()) {
            throw new IllegalArgumentException(
                    "Propietario, clave, tipo y solicitud canónica son obligatorios.");
        }
    }

    private String sha256(String material) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException imposible) {
            throw new IllegalStateException("SHA-256 no está disponible.", imposible);
        }
    }

    public String hashSolicitud(String materialCanonico) {
        return sha256(materialCanonico);
    }
}
