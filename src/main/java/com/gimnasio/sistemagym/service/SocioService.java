package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.ActualizarSocioRequest;
import com.gimnasio.sistemagym.dto.SocioRespuesta;
import com.gimnasio.sistemagym.entity.Auditoria;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.util.DocumentoIdentidad;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.AuditoriaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SocioService {

    private final SocioRepository socioRepository;
    private final GeneradorCodigoSocio generadorCodigoSocio;
    private final AuditoriaRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public SocioService(
            SocioRepository socioRepository,
            GeneradorCodigoSocio generadorCodigoSocio,
            AuditoriaRepository auditoriaRepository,
            UsuarioRepository usuarioRepository) {
        this.socioRepository = socioRepository;
        this.generadorCodigoSocio = generadorCodigoSocio;
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<SocioRespuesta> listarTodos() {
        return socioRepository.findAll().stream().map(this::aRespuesta).toList();
    }

    public SocioRespuesta buscarPorId(Long id) {
        validarId(id);
        return socioRepository.findById(id).map(this::aRespuesta)
                .orElseThrow(() -> noEncontrado(id));
    }

    public SocioRespuesta buscarPorCodigo(String codigoSocio) {
        return socioRepository.findByCodigoSocio(codigoSocio).map(this::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un socio con ese código."));
    }

    public SocioRespuesta buscarPorDni(String dni) {
        return socioRepository.findByDni(dni).map(this::aRespuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un socio con ese DNI."));
    }

    public List<SocioRespuesta> buscarPorApellidos(String apellidos) {
        return socioRepository.findByApellidosContainingIgnoreCase(apellidos)
                .stream().map(this::aRespuesta).toList();
    }

    public List<SocioRespuesta> buscarGlobal(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(
                    "El texto de búsqueda no puede estar vacío.");
        }
        return socioRepository.buscarGlobal(texto.trim()).stream()
                .map(this::aRespuesta).toList();
    }

    public List<SocioRespuesta> listarPorEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new IllegalArgumentException(
                    "El estado debe ser ACTIVO o INACTIVO.");
        }
        String estadoNormalizado = estado.trim().toUpperCase(java.util.Locale.ROOT);
        if (!estadoNormalizado.equals("ACTIVO")
                && !estadoNormalizado.equals("INACTIVO")) {
            throw new IllegalArgumentException(
                    "El estado debe ser ACTIVO o INACTIVO.");
        }
        return socioRepository.findByEstado(estadoNormalizado).stream()
                .map(this::aRespuesta).toList();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public SocioRespuesta registrar(Socio socio) {
        normalizarDocumento(socio, socio.getDni(), socio.getTipoDocumento(), socio.getNumeroDocumento());
        validarDocumento(socio.getTipoDocumento(), socio.getNumeroDocumento());
        validarUnicidad(socio, null);

        Socio socioGuardado;
        try {
            socioGuardado = generadorCodigoSocio.crearConSiguienteCodigo(codigo -> {
                socio.setCodigoSocio(codigo);
                socio.setEstado("ACTIVO");
                return socioRepository.saveAndFlush(socio);
            });
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictoException(
                    "Los datos del socio entran en conflicto con un registro existente.");
        }

        return aRespuesta(socioGuardado);
    }

    @Transactional
    public SocioRespuesta actualizar(Long id, ActualizarSocioRequest datosActualizados) {
        validarId(id);
        Socio socio = socioRepository.findById(id)
                .orElseThrow(() -> noEncontrado(id));

        String dniNuevo = datosActualizados.dni();
        normalizarDocumento(socio, dniNuevo, datosActualizados.tipoDocumento(), datosActualizados.numeroDocumento());
        validarDocumento(socio.getTipoDocumento(), socio.getNumeroDocumento());
        validarUnicidad(socio, id);
        socio.setNombres(datosActualizados.nombres());
        socio.setApellidos(datosActualizados.apellidos());
        socio.setFechaNacimiento(datosActualizados.fechaNacimiento());
        socio.setCelular(datosActualizados.celular());
        socio.setCorreo(datosActualizados.correo());

        return aRespuesta(socioRepository.save(socio));
    }

    @Transactional
    public SocioRespuesta activar(Long id, String motivo, String correoResponsable) {
        return cambiarEstado(id, motivo, correoResponsable, "ACTIVO", "INACTIVO",
                "ACTIVAR_SOCIO");
    }

    @Transactional
    public SocioRespuesta desactivar(Long id, String motivo, String correoResponsable) {
        return cambiarEstado(id, motivo, correoResponsable, "INACTIVO", "ACTIVO",
                "DESACTIVAR_SOCIO");
    }

    private SocioRespuesta cambiarEstado(Long id, String motivo, String correoResponsable,
            String estadoNuevo, String estadoAnteriorEsperado, String accion) {
        validarId(id);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio.");
        }

        Socio socio = socioRepository.findLockedById(id)
                .orElseThrow(() -> noEncontrado(id));
        String estadoAnterior = socio.getEstado();
        if (estadoNuevo.equalsIgnoreCase(estadoAnterior)) {
            throw new ConflictoException(
                    "El socio ya tiene el estado " + estadoNuevo + ".");
        }
        if (estadoAnterior == null
                || !estadoAnteriorEsperado.equalsIgnoreCase(estadoAnterior)) {
            throw new ConflictoException(
                    "El estado actual del socio no permite esta transición.");
        }

        var usuario = usuarioRepository.findByCorreo(correoResponsable)
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el usuario autenticado para la auditoría."));

        socio.setEstado(estadoNuevo);
        Socio socioActualizado = socioRepository.saveAndFlush(socio);
        Auditoria auditoria = new Auditoria(
                usuario.getIdUsuario(), accion, "SOCIO", String.valueOf(id),
                Map.of("motivo", motivo.trim(),
                        "estadoAnterior", estadoAnterior,
                        "estadoNuevo", estadoNuevo));
        auditoriaRepository.saveAndFlush(auditoria);
        return aRespuesta(socioActualizado);
    }

    private SocioRespuesta aRespuesta(Socio socio) {
        return new SocioRespuesta(
                socio.getIdSocio(), socio.getCodigoSocio(), socio.getDni(),
                socio.getTipoDocumento() != null ? socio.getTipoDocumento() : (socio.getDni() == null ? null : "DNI"),
                socio.getNumeroDocumento() != null ? socio.getNumeroDocumento() : socio.getDni(),
                socio.getNombres(), socio.getApellidos(), socio.getFechaNacimiento(),
                socio.getCelular(), socio.getCorreo(), socio.getEstado(),
                socio.getFechaRegistro());
    }

    private void normalizarDocumento(Socio socio, String dniLegado, String tipo, String numero) {
        String normalizedType = tipo == null || tipo.isBlank() ? null : tipo.trim().toUpperCase(java.util.Locale.ROOT);
        String normalizedNumber = numero == null || numero.isEmpty() ? null : numero;
        String legacy = dniLegado == null || dniLegado.isEmpty() ? null : dniLegado;
        if (normalizedType == null && normalizedNumber == null && legacy != null) {
            normalizedType = "DNI";
            normalizedNumber = legacy;
        } else if (normalizedType == null && normalizedNumber != null) {
            throw new IllegalArgumentException("Selecciona el tipo de documento.");
        } else if (normalizedType != null && normalizedNumber == null) {
            throw new IllegalArgumentException("Ingresa el número del documento seleccionado.");
        }
        if (legacy != null && normalizedType != null && (!"DNI".equals(normalizedType) || !legacy.equals(normalizedNumber))) {
            throw new IllegalArgumentException("El documento enviado en formato antiguo no coincide con el documento tipado.");
        }
        if (normalizedType != null && normalizedNumber != null) {
            normalizedNumber = DocumentoIdentidad.normalizar(
                    TipoDocumento.valueOf(normalizedType), normalizedNumber);
        }
        socio.setTipoDocumento(normalizedType);
        socio.setNumeroDocumento(normalizedNumber);
        // Mantener el campo legado y su índice único para clientes y datos históricos.
        socio.setDni("DNI".equals(normalizedType) ? normalizedNumber : null);
    }

    private void validarDocumento(String tipo, String numero) {
        if (tipo == null && numero == null) return;
        if (tipo == null) return;
        DocumentoIdentidad.normalizar(TipoDocumento.valueOf(tipo), numero);
    }

    private void validarUnicidad(Socio socio, Long idActual) {
        String tipo = socio.getTipoDocumento();
        String numero = socio.getNumeroDocumento();
        if (tipo == null) return;
        Optional<Socio> coincidencia = socioRepository.findByTipoDocumentoAndNumeroDocumento(tipo, numero);
        if (coincidencia.isPresent() && !java.util.Objects.equals(coincidencia.get().getIdSocio(), idActual))
            throw new ConflictoException("Ya existe un socio con ese documento.");
        if ("DNI".equals(tipo)) {
            Optional<Socio> legacy = socioRepository.findByDni(numero);
            if (legacy.isPresent() && !java.util.Objects.equals(legacy.get().getIdSocio(), idActual))
                throw new ConflictoException("Ya existe un socio con ese documento.");
        }
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("El ID debe ser mayor que cero.");
        }
    }

    private RecursoNoEncontradoException noEncontrado(Long id) {
        return new RecursoNoEncontradoException(
                "No existe un socio con el ID " + id + ".");
    }
}
