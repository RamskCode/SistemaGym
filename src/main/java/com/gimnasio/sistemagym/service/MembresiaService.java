package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.repository.SocioRepository;
import com.gimnasio.sistemagym.repository.PlanRepository;
import com.gimnasio.sistemagym.entity.Socio;
import com.gimnasio.sistemagym.entity.Plan;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.Pago;
import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import com.gimnasio.sistemagym.entity.Usuario;
import com.gimnasio.sistemagym.dto.MembresiaRespuesta;
import com.gimnasio.sistemagym.dto.PagoPosteriorMembresiaRespuesta;
import com.gimnasio.sistemagym.dto.RegistrarMembresiaRequest;
import com.gimnasio.sistemagym.dto.RegistrarPagoPosteriorRequest;
import com.gimnasio.sistemagym.dto.RegistroMembresiaRespuesta;
import com.gimnasio.sistemagym.exception.ConflictoException;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import java.util.List;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;


@Service
public class MembresiaService {


    private final MembresiaRepository membresiaRepository;
    private final SocioRepository socioRepository;
    private final PlanRepository planRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoService pagoService;
    private final Clock reloj;

    public MembresiaService(
            MembresiaRepository membresiaRepository,
            SocioRepository socioRepository,
            PlanRepository planRepository,
            UsuarioRepository usuarioRepository,
            PagoService pagoService,
            Clock reloj) {

        this.membresiaRepository = membresiaRepository;
        this.socioRepository = socioRepository;
        this.planRepository = planRepository;
        this.usuarioRepository = usuarioRepository;
        this.pagoService = pagoService;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<MembresiaRespuesta> listarTodas() {
        return membresiaRepository.findAll()
                .stream()
                .map(MembresiaRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public MembresiaRespuesta buscarPorId(Long id) {
        return membresiaRepository.findById(id)
                .map(MembresiaRespuesta::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la membresia con ID: " + id
                ));
    }

    @Transactional(readOnly = true)
    public List<MembresiaRespuesta> buscarPorSocio(Long idSocio) {
        return membresiaRepository.findBySocio_IdSocio(idSocio)
                .stream()
                .map(MembresiaRespuesta::desde)
                .toList();
    }

    @Transactional
    public RegistroMembresiaRespuesta registrar(
            RegistrarMembresiaRequest solicitud,
            String correoUsuarioAutenticado) {


        Socio socio = socioRepository.findById(solicitud.getIdSocio())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el socio con ID: " + solicitud.getIdSocio()));
        Plan plan = planRepository.findById(solicitud.getIdPlan())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el plan con ID: " + solicitud.getIdPlan()));

        if (!"ACTIVO".equalsIgnoreCase(socio.getEstado())) {
            throw new ConflictoException(
                    "No se puede registrar una membresia para un socio inactivo.");
        }
        if (!"ACTIVO".equalsIgnoreCase(plan.getEstado())) {
            throw new ConflictoException(
                    "No se puede registrar una membresia con un plan inactivo.");
        }
        if (solicitud.getMontoPrimerPago() == null
                || solicitud.getMontoPrimerPago().compareTo(new BigDecimal("40.00")) < 0) {
            throw new IllegalArgumentException(
                    "El primer pago debe ser de al menos S/ 40.00.");
        }
        if (solicitud.getMontoPrimerPago().compareTo(plan.getPrecio()) > 0) {
            throw new IllegalArgumentException(
                    "El primer pago no puede superar el importe pendiente de la membresia.");
        }

        Usuario usuario = usuarioRepository.findByCorreo(correoUsuarioAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario autenticado."));

        LocalDate fechaInicio = LocalDate.now(reloj);
        Membresia membresia = new Membresia();
        membresia.setSocio(socio);
        membresia.setPlan(plan);
        membresia.setPrecioAcordado(plan.getPrecio());
        membresia.setFechaInicio(fechaInicio);
        membresia.setFechaVencimiento(
                fechaInicio.plusDays(plan.getDuracionDias()));
        membresia.setEstado("PENDIENTE_PAGO");
        Membresia membresiaGuardada = membresiaRepository.saveAndFlush(membresia);

        Pago pago = pagoService.registrarPago(
                usuario.getIdUsuario(),
                solicitud.getMontoPrimerPago(),
                solicitud.getMetodoPago(),
                solicitud.getReferencia(),
                solicitud.getObservaciones());
        var aplicacion = pagoService.aplicarPagoAMembresia(
                pago.getIdPago(),
                membresiaGuardada.getIdMembresia(),
                solicitud.getMontoPrimerPago(),
                TipoAplicacionPago.INICIAL);

        return new RegistroMembresiaRespuesta(
                membresiaGuardada.getIdMembresia(),
                socio.getIdSocio(),
                plan.getIdPlan(),
                membresiaGuardada.getPrecioAcordado(),
                membresiaGuardada.getFechaInicio(),
                membresiaGuardada.getFechaVencimiento(),
                membresiaGuardada.getFechaLimitePago(),
                membresiaGuardada.getEstado(),
                pago.getIdPago(),
                pago.getMonto(),
                pago.getMetodoPago(),
                aplicacion.getIdAplicacion());
    }

    @Transactional
    public PagoPosteriorMembresiaRespuesta registrarPagoPosterior(
            Long idMembresia,
            RegistrarPagoPosteriorRequest solicitud,
            String correoUsuarioAutenticado) {

        if (idMembresia == null || idMembresia <= 0) {
            throw new IllegalArgumentException(
                    "El ID de la membresía debe ser mayor que cero.");
        }
        if (solicitud == null
                || solicitud.monto() == null
                || solicitud.monto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El importe del pago debe ser mayor que cero.");
        }

        Membresia membresia = membresiaRepository.findById(idMembresia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la membresia con ID: " + idMembresia));

        if (!"PENDIENTE_PAGO".equals(membresia.getEstado())
                && !"ACTIVA".equals(membresia.getEstado())) {
            throw new ConflictoException(
                    "No se pueden registrar pagos para una membresia en estado "
                            + membresia.getEstado() + ".");
        }

        BigDecimal saldoPendiente =
                pagoService.calcularSaldoPendiente(idMembresia);
        if (saldoPendiente.signum() <= 0) {
            throw new ConflictoException(
                    "La membresia ya se encuentra pagada.");
        }
        if (solicitud.monto().compareTo(saldoPendiente) > 0) {
            throw new IllegalArgumentException(
                    "El importe del pago no puede superar el saldo pendiente de la membresia.");
        }

        Usuario usuario = usuarioRepository.findByCorreo(correoUsuarioAutenticado)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro el usuario autenticado."));

        Pago pago = pagoService.registrarPago(
                usuario.getIdUsuario(),
                solicitud.monto(),
                solicitud.metodoPago(),
                solicitud.referencia(),
                solicitud.observaciones());

        var aplicacion = pagoService.aplicarPagoPosteriorAMembresia(
                pago.getIdPago(),
                idMembresia,
                solicitud.monto());

        BigDecimal saldoActualizado =
                pagoService.calcularSaldoPendiente(idMembresia);
        Membresia membresiaActualizada = membresiaRepository.findById(idMembresia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la membresia con ID: " + idMembresia));

        return new PagoPosteriorMembresiaRespuesta(
                pago.getIdPago(),
                aplicacion.getIdAplicacion(),
                idMembresia,
                pago.getMonto(),
                aplicacion.getMontoAplicado(),
                pago.getMetodoPago(),
                pago.getReferencia(),
                pago.getObservaciones(),
                pago.getFechaPago(),
                saldoActualizado,
                membresiaActualizada.getEstado());
    }
}
