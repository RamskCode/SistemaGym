package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.IngresoRespuesta;
import com.gimnasio.sistemagym.dto.IngresoMembresiaEstadoRespuesta;
import com.gimnasio.sistemagym.dto.IngresoSocioHistorialRespuesta;
import com.gimnasio.sistemagym.dto.PaginaIngresoSocioRespuesta;
import com.gimnasio.sistemagym.dto.PaginaIngresoMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.PaseDiarioHistorialRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarIngresoRequest;
import com.gimnasio.sistemagym.entity.ContactoPromocional;
import com.gimnasio.sistemagym.entity.Ingreso;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.TipoDocumento;
import com.gimnasio.sistemagym.util.DocumentoIdentidad;
import com.gimnasio.sistemagym.entity.TipoIngreso;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.ContactoPromocionalRepository;
import com.gimnasio.sistemagym.repository.IngresoRepository;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.text.Normalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
public class IngresoService {

    private static final String RESULTADO_ACEPTADO = "ACEPTADO";

    private final SocioRepository socioRepository;
    private final GeneradorCodigoSocio generadorCodigoSocio;
    private final MembresiaRepository membresiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ContactoPromocionalRepository contactoRepository;
    private final IngresoRepository ingresoRepository;
    private final AplicacionPagoMembresiaRepository aplicacionPagoRepository;
    private final PagoService pagoService;
    private final ConfiguracionService configuracionService;
    private final Clock relojMembresias;

    @Value("${sistemagym.pases-diarios.esquema-v2-habilitado:false}")
    private boolean esquemaPaseDiarioV2Habilitado;

    public IngresoService(
            SocioRepository socioRepository,
            GeneradorCodigoSocio generadorCodigoSocio,
            MembresiaRepository membresiaRepository,
            UsuarioRepository usuarioRepository,
            ContactoPromocionalRepository contactoRepository,
            IngresoRepository ingresoRepository,
            AplicacionPagoMembresiaRepository aplicacionPagoRepository,
            PagoService pagoService,
            ConfiguracionService configuracionService,
            @Qualifier("relojMembresias") Clock relojMembresias) {

        this.socioRepository = socioRepository;
        this.generadorCodigoSocio = generadorCodigoSocio;
        this.membresiaRepository = membresiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.contactoRepository = contactoRepository;
        this.ingresoRepository = ingresoRepository;
        this.aplicacionPagoRepository = aplicacionPagoRepository;
        this.pagoService = pagoService;
        this.configuracionService = configuracionService;
        this.relojMembresias = relojMembresias;
    }

    /**
     * Registra un ingreso aceptado. El pago del pase diario (solo visitantes)
     * y el primer ingreso se guardan en la misma transacción. Un pase pagado
     * habilita varios accesos el mismo día de Lima; cada reingreso apunta al
     * ingreso de venta y no crea otro pago.
     */
    @Transactional
    public IngresoRespuesta registrar(
            RegistrarIngresoRequest solicitud,
            String correoUsuarioAutenticado) {

        if (!esquemaPaseDiarioV2Habilitado) {
            return registrarConEsquemaAnterior(solicitud, correoUsuarioAutenticado);
        }

        if (solicitud == null) {
            throw new IllegalArgumentException(
                    "Los datos del ingreso son obligatorios.");
        }

        Documento documento = normalizarDocumento(
                solicitud.tipoDocumento(), solicitud.numeroDocumento());

        String clave = solicitud.claveIdempotencia();
        if (clave == null || !esUuid(clave)) {
            throw new IllegalArgumentException("La clave de idempotencia es obligatoria y debe ser UUID.");
        }
        String hashSolicitud = hashSolicitud(solicitud, documento);

        Usuario usuario = usuarioRepository.findByCorreo(correoUsuarioAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario autenticado."));

        Ingreso operacionAnterior = ingresoRepository.findByClaveIdempotencia(clave).orElse(null);
        if (operacionAnterior != null) {
            if (!hashSolicitud.equals(operacionAnterior.getHashSolicitud())) {
                throw new ConflictoException("La clave de operación ya se utilizó con otros datos.");
            }
            return construirRespuesta(operacionAnterior, false, null, "IDEMPOTENTE");
        }

        Socio socio = resolverSocio(solicitud, documento);
        if (socio != null && "ACTIVO".equalsIgnoreCase(socio.getEstado())
                && tieneMembresiaVigente(socio)) {
            return registrarSocio(socio, documento, usuario, clave, hashSolicitud);
        }

        if (socio == null) {
            socio = crearSocioVisitante(solicitud, documento);
        }
        if (!"ACTIVO".equalsIgnoreCase(socio.getEstado())) {
            throw new ConflictoException("El socio se encuentra inactivo; no puede comprar un pase diario.");
        }
        vincularContactoLegacy(documento, socio);
        Ingreso paseExistente = buscarPaseDiarioVigente(socio);
        if (paseExistente != null) {
            Ingreso reingreso = crearIngreso(TipoIngreso.VISITANTE, documento,
                    socio, null, usuario, null, paseExistente, clave, hashSolicitud);
            return construirRespuesta(reingreso, false, null, "REINGRESO_AUTORIZADO");
        }
        return registrarVisitante(solicitud, documento, usuario, socio, clave, hashSolicitud);
    }

    public boolean isEsquemaPaseDiarioV2Habilitado() {
        return esquemaPaseDiarioV2Habilitado;
    }

    @Transactional(readOnly = true)
    public List<PaseDiarioHistorialRespuesta> listarPasesDiariosPorSocio(Long idSocio) {
        if (!socioRepository.existsById(idSocio)) {
            throw new RecursoNoEncontradoException("No se encontró el socio solicitado.");
        }
        return ingresoRepository.listarPasesDiariosPorSocio(idSocio).stream()
                .map(PaseDiarioHistorialRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public IngresoMembresiaEstadoRespuesta evaluarIngresoMembresia(Long idSocio) {
        Socio socio = socioRepository.findById(idSocio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el socio solicitado."));
        List<Membresia> registros = membresiaRepository.findBySocio_IdSocio(idSocio);
        LocalDate hoy = LocalDate.now(relojMembresias);
        Membresia membresia = registros.stream()
                .filter(item -> "ACTIVA".equalsIgnoreCase(item.getEstado())
                        && estaVigente(item) && pagoPermiteIngreso(item, hoy))
                .max(Comparator.comparing(Membresia::getFechaVencimiento))
                .orElseGet(() -> registros.stream()
                        .filter(item -> "ACTIVA".equalsIgnoreCase(item.getEstado()) && estaVigente(item))
                        .max(Comparator.comparing(Membresia::getFechaVencimiento))
                        .orElseGet(() -> registros.stream().max(Comparator
                        .comparing(Membresia::getFechaCreacion,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(Membresia::getIdMembresia,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .orElse(null)));

        boolean socioActivo = "ACTIVO".equalsIgnoreCase(socio.getEstado());
        boolean membresiaAutorizada = membresia != null
                && "ACTIVA".equalsIgnoreCase(membresia.getEstado())
                && estaVigente(membresia) && pagoPermiteIngreso(membresia, hoy);
        String motivo = socioActivo ? motivoMembresia(membresia, hoy)
                : "El socio está inactivo y no puede registrar una entrada.";
        String tipoDocumento = socio.getTipoDocumento() == null
                ? (socio.getDni() == null ? null : "DNI") : socio.getTipoDocumento();
        String numeroDocumento = socio.getNumeroDocumento() == null
                ? socio.getDni() : socio.getNumeroDocumento();

        return new IngresoMembresiaEstadoRespuesta(
                socio.getIdSocio(), socio.getCodigoSocio(), tipoDocumento, numeroDocumento,
                socio.getNombres(), socio.getApellidos(), socio.getEstado(),
                socioActivo && membresiaAutorizada, motivo,
                membresia == null ? null : new IngresoMembresiaEstadoRespuesta.Membresia(
                        membresia.getIdMembresia(),
                        membresia.getPlan() == null ? "Plan no disponible" : membresia.getPlan().getNombre(),
                        membresia.getEstado(), membresia.getFechaInicio(), membresia.getFechaVencimiento()));
    }

    @Transactional(readOnly = true)
    public PaginaIngresoMembresiaRespuesta listarHabilitadosParaIngreso(
            String nombre, String tipoDocumento, String numeroDocumento,
            int pagina, int tamano, org.springframework.data.domain.Sort sort) {
        LocalDate hoy = LocalDate.now(relojMembresias);
        String busquedaNombre = nombre == null ? "" : Normalizer.normalize(
                nombre.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String tipo = tipoDocumento == null ? "" : tipoDocumento.trim().toUpperCase(Locale.ROOT);
        if (!tipo.isEmpty() && !tipo.equals("DNI") && !tipo.equals("CE")) {
            throw new IllegalArgumentException("El tipo de documento no es válido.");
        }
        String numero = numeroDocumento == null ? "" : numeroDocumento;
        int limite = "DNI".equals(tipo) ? 8 : 12;
        if (!numero.matches("\\d*") || numero.length() > limite) {
            throw new IllegalArgumentException("El número de documento no es válido para el tipo seleccionado.");
        }
        Page<Socio> resultado = socioRepository.listarHabilitadosParaIngreso(
                busquedaNombre, tipo, numero, hoy,
                PageRequest.of(pagina, tamano, sort));
        return new PaginaIngresoMembresiaRespuesta(
                resultado.getContent().stream()
                        .map(socio -> evaluarIngresoMembresia(socio.getIdSocio())).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PaginaIngresoSocioRespuesta listarHistorialSocio(Long idSocio, int pagina, int tamano) {
        if (!socioRepository.existsById(idSocio)) {
            throw new RecursoNoEncontradoException("No se encontró el socio solicitado.");
        }
        Page<Ingreso> resultado = ingresoRepository.listarHistorialSocio(
                idSocio, PageRequest.of(pagina, tamano));
        return new PaginaIngresoSocioRespuesta(
                resultado.getContent().stream().map(ingreso -> new IngresoSocioHistorialRespuesta(
                        ingreso.getIdIngreso(), ingreso.getFechaHora(), ingreso.getTipoIngreso().name())).toList(),
                resultado.getNumber(), resultado.getSize(), resultado.getTotalElements(),
                resultado.getTotalPages());
    }

    @Transactional
    public IngresoRespuesta registrarIngresoMembresia(
            Long idSocio, String claveIdempotencia, String correoUsuarioAutenticado) {
        Socio socio = socioRepository.findById(idSocio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el socio solicitado."));
        String numero = socio.getNumeroDocumento() == null ? socio.getDni() : socio.getNumeroDocumento();
        String tipo = socio.getTipoDocumento() == null ? "DNI" : socio.getTipoDocumento();
        Documento documento = normalizarDocumento(tipo, numero);
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuarioAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));
        RegistrarIngresoRequest solicitud = new RegistrarIngresoRequest(
                documento.tipo().name(), documento.numero(), socio.getIdSocio(),
                socio.getNombres(), socio.getApellidos(), null, null, null, null, claveIdempotencia);

        if (esquemaPaseDiarioV2Habilitado) {
            if (claveIdempotencia == null || !esUuid(claveIdempotencia)) {
                throw new IllegalArgumentException("La clave de operación debe ser UUID.");
            }
            String hash = hashSolicitud(solicitud, documento);
            Ingreso anterior = ingresoRepository.findByClaveIdempotencia(claveIdempotencia).orElse(null);
            if (anterior != null) {
                if (!hash.equals(anterior.getHashSolicitud()) || anterior.getTipoIngreso() != TipoIngreso.SOCIO) {
                    throw new ConflictoException("La clave de operación ya se utilizó con otros datos.");
                }
                return construirRespuesta(anterior, false, null, "IDEMPOTENTE");
            }
            return registrarSocio(socio, documento, usuario, claveIdempotencia, hash);
        }

        verificarIngresoUnico(documento);
        return registrarSocio(socio, documento, usuario);
    }

    private String motivoMembresia(Membresia membresia, LocalDate hoy) {
        if (membresia == null) return "El socio no tiene membresías registradas.";
        if (!"ACTIVA".equalsIgnoreCase(membresia.getEstado())) {
            return switch (membresia.getEstado().toUpperCase(Locale.ROOT)) {
                case "PENDIENTE_PAGO" -> "La membresía está pendiente de pago.";
                case "VENCIDA" -> "La membresía está vencida.";
                case "CANCELADA" -> "La membresía está cancelada.";
                default -> "La membresía no está activa.";
            };
        }
        if (hoy.isBefore(membresia.getFechaInicio())) return "La vigencia de la membresía aún no comienza.";
        if (hoy.isAfter(membresia.getFechaVencimiento())) return "La membresía está vencida.";
        if (!pagoPermiteIngreso(membresia, hoy)) {
            return "El plazo de pago de la membresía venció y mantiene un saldo pendiente.";
        }
        return "Acceso autorizado.";
    }

    boolean pagoPermiteIngreso(Membresia membresia, LocalDate hoy) {
        BigDecimal totalAplicado = aplicacionPagoRepository.sumarAplicacionesPorMembresia(
                membresia.getIdMembresia());
        BigDecimal saldo = membresia.getPrecioAcordado().subtract(totalAplicado);
        if (saldo.signum() < 0) {
            throw new IllegalStateException("El total aplicado supera el precio de la membresía.");
        }
        return saldo.signum() == 0 || membresia.getFechaLimitePago() == null
                || !hoy.isAfter(membresia.getFechaLimitePago());
    }

    @Transactional
    public IngresoRespuesta registrarConEsquemaAnterior(
            RegistrarIngresoRequest solicitud, String correoUsuarioAutenticado) {
        if (solicitud == null) throw new IllegalArgumentException("Los datos del ingreso son obligatorios.");
        Documento documento = normalizarDocumento(solicitud.tipoDocumento(), solicitud.numeroDocumento());
        Usuario usuario = usuarioRepository.findByCorreo(correoUsuarioAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontro el usuario autenticado."));
        verificarIngresoUnico(documento);
        Socio socio = resolverSocio(solicitud, documento);
        if (socio != null) return registrarSocio(socio, documento, usuario);
        ContactoPromocional contacto = contactoRepository
                .findByTipoDocumentoAndNumeroDocumento(documento.tipo(), documento.numero())
                .orElseGet(() -> crearVisitante(solicitud, documento));
        return cobrarPaseConContacto(solicitud, documento, usuario, contacto);
    }

    private IngresoRespuesta cobrarPaseConContacto(RegistrarIngresoRequest solicitud,
            Documento documento, Usuario usuario, ContactoPromocional contacto) {
        BigDecimal precio = configuracionService.obtenerPrecioPaseDiario();
        String metodoPago = solicitud.metodoPago() == null ? null
                : solicitud.metodoPago().trim().toUpperCase(Locale.ROOT);
        Pago pago = pagoService.registrarPago(usuario.getIdUsuario(), precio, metodoPago,
                opcional(solicitud.referencia()), null);
        Ingreso ingreso = crearIngreso(TipoIngreso.VISITANTE, documento, null, contacto, usuario, pago);
        return construirRespuesta(ingreso, true, pago.getMonto());
    }

    private Socio resolverSocio(
            RegistrarIngresoRequest solicitud, Documento documento) {

        if (solicitud.idSocio() != null) {
            Socio socio = socioRepository.findById(solicitud.idSocio())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "No se encontro el socio con ID: "
                                    + solicitud.idSocio()));
            String numeroRegistrado = socio.getNumeroDocumento() == null
                    ? socio.getDni() : socio.getNumeroDocumento();
            String tipoRegistrado = socio.getTipoDocumento() == null ? "DNI" : socio.getTipoDocumento();
            if (numeroRegistrado == null || !numeroRegistrado.trim().equals(documento.numero())
                    || !tipoRegistrado.equals(documento.tipo().name())) {
                throw new IllegalArgumentException("El documento enviado no corresponde al socio indicado.");
            }
            return socio;
        }

        return socioRepository.findByTipoDocumentoAndNumeroDocumento(
                        documento.tipo().name(), documento.numero())
                .or(() -> documento.tipo() == TipoDocumento.DNI
                        ? socioRepository.findByDni(documento.numero()) : java.util.Optional.empty())
                .orElse(null);
    }

    private IngresoRespuesta registrarSocio(
            Socio socio, Documento documento, Usuario usuario) {
        return registrarSocio(socio, documento, usuario, null, null);
    }

    private IngresoRespuesta registrarSocio(
            Socio socio, Documento documento, Usuario usuario, String clave, String hashSolicitud) {

        String numeroRegistrado = socio.getNumeroDocumento() == null
                ? socio.getDni() : socio.getNumeroDocumento().trim();
        String tipoRegistrado = socio.getTipoDocumento() == null ? "DNI" : socio.getTipoDocumento();
        if (numeroRegistrado == null || !numeroRegistrado.equals(documento.numero())
                || !tipoRegistrado.equals(documento.tipo().name())) {
            throw new IllegalArgumentException(
                    "El documento enviado no corresponde al socio indicado.");
        }

        if (!"ACTIVO".equalsIgnoreCase(socio.getEstado())) {
            throw new ConflictoException(
                    "El socio se encuentra inactivo; no puede registrar su ingreso.");
        }

        if (!tieneMembresiaVigente(socio)) {
            throw new ConflictoException(
                    "El socio no tiene una membresía activa vigente para la fecha actual.");
        }

        Ingreso ingreso = crearIngreso(
                TipoIngreso.SOCIO, documento, socio, null, usuario, null,
                null, clave, hashSolicitud);

        return construirRespuesta(ingreso, false, null);
    }

    private IngresoRespuesta registrarVisitante(
            RegistrarIngresoRequest solicitud,
            Documento documento,
            Usuario usuario,
            Socio socio,
            String clave,
            String hashSolicitud) {

        BigDecimal precio = configuracionService.obtenerPrecioPaseDiario();

        String metodoPago = solicitud.metodoPago() == null
                ? null
                : solicitud.metodoPago().trim().toUpperCase(Locale.ROOT);

        Pago pago = pagoService.registrarPago(
                usuario.getIdUsuario(),
                precio,
                metodoPago,
                opcional(solicitud.referencia()),
                null);

        Ingreso ingreso = crearIngreso(
                TipoIngreso.VISITANTE, documento, socio, null, usuario, pago,
                null, clave, hashSolicitud);

        return construirRespuesta(ingreso, true, pago.getMonto());
    }

    private Socio crearSocioVisitante(RegistrarIngresoRequest solicitud, Documento documento) {
        String nombres = requerido(solicitud.nombre(), "El nombre de la persona es obligatorio.");
        String apellidos = requerido(solicitud.apellidos(), "Los apellidos de la persona son obligatorios.");
        Socio persona = new Socio();
        persona.setDni(documento.tipo() == TipoDocumento.DNI ? documento.numero() : null);
        persona.setTipoDocumento(documento.tipo().name());
        persona.setNumeroDocumento(documento.numero());
        persona.setNombres(nombres);
        persona.setApellidos(apellidos);
        persona.setCelular(opcional(solicitud.celular()));
        persona.setCorreo(opcional(solicitud.correo()));
        persona.setEstado("ACTIVO");
        try {
            return generadorCodigoSocio.crearConSiguienteCodigo(codigo -> {
                persona.setCodigoSocio(codigo);
                return socioRepository.saveAndFlush(persona);
            });
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ConflictoException(
                    "No se pudo registrar la persona; verifica si el documento ya existe.");
        }
    }

    private void vincularContactoLegacy(Documento documento, Socio socio) {
        contactoRepository.findByTipoDocumentoAndNumeroDocumento(documento.tipo(), documento.numero())
                .ifPresent(contacto -> {
                    if (contacto.getSocio() != null
                            && !contacto.getSocio().getIdSocio().equals(socio.getIdSocio())) {
                        throw new ConflictoException(
                                "El contacto histórico está vinculado a otra ficha; requiere revisión administrativa.");
                    }
                    contacto.setSocio(socio);
                    contactoRepository.saveAndFlush(contacto);
                });
    }

    private ContactoPromocional crearVisitante(
            RegistrarIngresoRequest solicitud, Documento documento) {

        ContactoPromocional contacto = new ContactoPromocional();
        contacto.setTipoDocumento(documento.tipo());
        contacto.setNumeroDocumento(documento.numero());
        contacto.setNombre(requerido(
                solicitud.nombre(), "El nombre del visitante es obligatorio."));
        contacto.setCelular(requerido(
                solicitud.celular(), "El celular del visitante es obligatorio."));
        contacto.setCorreo(opcional(solicitud.correo()));

        return contactoRepository.saveAndFlush(contacto);
    }

    private Ingreso crearIngreso(
            TipoIngreso tipoIngreso,
            Documento documento,
            Socio socio,
            ContactoPromocional contacto,
            Usuario usuario,
            Pago pago) {

        return crearIngreso(tipoIngreso, documento, socio, contacto, usuario, pago, null);
    }

    private Ingreso crearIngreso(
            TipoIngreso tipoIngreso,
            Documento documento,
            Socio socio,
            ContactoPromocional contacto,
            Usuario usuario,
            Pago pago,
            Ingreso paseOrigen) {

        return crearIngreso(tipoIngreso, documento, socio, contacto, usuario, pago,
                paseOrigen, null, null);
    }

    private Ingreso crearIngreso(
            TipoIngreso tipoIngreso,
            Documento documento,
            Socio socio,
            ContactoPromocional contacto,
            Usuario usuario,
            Pago pago,
            Ingreso paseOrigen,
            String clave,
            String hashSolicitud) {

        Ingreso ingreso = new Ingreso();
        ingreso.setTipoIngreso(tipoIngreso);
        ingreso.setTipoDocumento(documento.tipo());
        ingreso.setNumeroDocumento(documento.numero());
        ingreso.setSocio(socio);
        ingreso.setContacto(contacto);
        ingreso.setUsuario(usuario);
        ingreso.setPago(pago);
        ingreso.setPaseOrigen(paseOrigen);
        ingreso.setClaveIdempotencia(clave);
        ingreso.setHashSolicitud(hashSolicitud);
        ingreso.setFechaHora(LocalDateTime.now(relojMembresias));

        return ingresoRepository.saveAndFlush(ingreso);
    }

    private IngresoRespuesta construirRespuesta(
            Ingreso ingreso, boolean paseCobrado, BigDecimal montoCobrado) {

        return construirRespuesta(ingreso, paseCobrado, montoCobrado, RESULTADO_ACEPTADO);
    }

    private IngresoRespuesta construirRespuesta(
            Ingreso ingreso, boolean paseCobrado, BigDecimal montoCobrado, String resultado) {

        return new IngresoRespuesta(
                ingreso.getIdIngreso(),
                ingreso.getTipoIngreso().name(),
                ingreso.getSocio() == null
                        ? null
                        : ingreso.getSocio().getIdSocio(),
                ingreso.getContacto() == null
                        ? null
                        : ingreso.getContacto().getIdContacto(),
                ingreso.getTipoDocumento().name(),
                ingreso.getNumeroDocumento(),
                ingreso.getFechaHora(),
                paseCobrado,
                montoCobrado,
                ingreso.getPago() == null ? null : ingreso.getPago().getIdPago(),
                resultado);
    }

    private boolean tieneMembresiaVigente(Socio socio) {
        LocalDate hoy = LocalDate.now(relojMembresias);
        return membresiaRepository.findBySocio_IdSocio(socio.getIdSocio())
                .stream()
                .filter(membresia -> "ACTIVA".equalsIgnoreCase(
                        membresia.getEstado()))
                .anyMatch(membresia -> estaVigente(membresia)
                        && pagoPermiteIngreso(membresia, hoy));
    }

    boolean estaVigente(Membresia membresia) {
        LocalDate fecha = LocalDate.now(relojMembresias);
        return !fecha.isBefore(membresia.getFechaInicio())
                && !fecha.isAfter(membresia.getFechaVencimiento());
    }

    private Ingreso buscarPaseDiarioVigente(Socio socio) {
        LocalDate hoy = LocalDate.now(relojMembresias);
        return ingresoRepository.buscarPaseDiarioVigente(socio.getIdSocio(),
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()).orElse(null);
    }

    private void verificarIngresoUnico(Documento documento) {
        LocalDate hoy = LocalDate.now(relojMembresias);
        boolean yaRegistrado = ingresoRepository
                .existsByTipoDocumentoAndNumeroDocumentoAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
                        documento.tipo(), documento.numero(), hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        if (yaRegistrado) {
            throw new ConflictoException("Ya se registró un ingreso para el documento "
                    + documento.tipo() + " " + documento.numero() + " el día de hoy.");
        }
    }

    private Documento normalizarDocumento(String tipo, String numero) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException(
                    "El tipo de documento es obligatorio.");
        }

        String tipoNormalizado = tipo.trim().toUpperCase(Locale.ROOT);
        if (!TipoDocumento.DNI.name().equals(tipoNormalizado)
                && !TipoDocumento.CE.name().equals(tipoNormalizado)) {
            throw new IllegalArgumentException(
                    "El tipo de documento debe ser DNI o CE.");
        }

        if (numero == null || numero.isBlank()) {
            throw new IllegalArgumentException(
                    "El número de documento es obligatorio.");
        }

        TipoDocumento tipoNormalizadoEnum = TipoDocumento.valueOf(tipoNormalizado);
        return new Documento(tipoNormalizadoEnum,
                DocumentoIdentidad.normalizar(tipoNormalizadoEnum, numero));
    }

    private boolean esUuid(String value) {
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (IllegalArgumentException ex) { return false; }
    }

    private String hashSolicitud(RegistrarIngresoRequest request, Documento documento) {
        String canonico = documento.tipo().name() + "|" + documento.numero() + "|"
                + request.idSocio() + "|" + textoHash(request.nombre()) + "|"
                + textoHash(request.apellidos()) + "|" + textoHash(request.metodoPago()).toUpperCase(Locale.ROOT)
                + "|" + textoHash(request.referencia());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no está disponible.", impossible);
        }
    }

    private String textoHash(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean contieneEspacios(String valor) {
        return valor.chars().anyMatch(caracter ->
                Character.isWhitespace(caracter)
                        || Character.isSpaceChar(caracter));
    }

    private String requerido(String valor, String mensaje) {
        String normalizado = opcional(valor);
        if (normalizado == null) {
            throw new IllegalArgumentException(mensaje);
        }
        return normalizado;
    }

    private String opcional(String valor) {
        if (valor == null) {
            return null;
        }
        String normalizado = valor.trim();
        return normalizado.isEmpty() ? null : normalizado;
    }

    private record Documento(TipoDocumento tipo, String numero) {
    }
}
