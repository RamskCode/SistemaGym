package com.gimnasio.sistemagym.service;

import java.util.List;
import java.time.Clock;
import java.time.LocalDate;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.entity.AplicacionPagoMembresia;
import com.gimnasio.sistemagym.entity.TipoAplicacionPago;
import org.springframework.transaction.annotation.Transactional;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import com.gimnasio.sistemagym.entity.Pago;
import java.math.BigDecimal;
import java.util.Set;
import com.gimnasio.sistemagym.repository.PagoRepository;
import com.gimnasio.sistemagym.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import com.gimnasio.sistemagym.entity.Usuario;
import jakarta.persistence.EntityNotFoundException;

@Service
public class PagoService {

    private final AplicacionPagoMembresiaRepository aplicacionPagoMembresiaRepository;
    private final MembresiaRepository membresiaRepository;
    private final PagoRepository pagoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MembresiaCicloVidaService membresiaCicloVidaService;
    private final Clock reloj;

    public PagoService(
            PagoRepository pagoRepository,
            UsuarioRepository usuarioRepository,
            MembresiaRepository membresiaRepository,
            AplicacionPagoMembresiaRepository aplicacionPagoMembresiaRepository,
            MembresiaCicloVidaService membresiaCicloVidaService,
            Clock reloj) {

        this.pagoRepository = pagoRepository;
        this.usuarioRepository = usuarioRepository;
        this.membresiaRepository = membresiaRepository;
        this.aplicacionPagoMembresiaRepository =
                aplicacionPagoMembresiaRepository;
        this.membresiaCicloVidaService = membresiaCicloVidaService;
        this.reloj = reloj;
    }

    public Usuario buscarUsuario(Long idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No se encontró el usuario con ID: " + idUsuario
                ));
    }

    public Pago registrarPago(
            Long idUsuario,
            BigDecimal monto,
            String metodoPago,
            String referencia,
            String observaciones) {

        // 1. Validar el importe.
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El importe del pago debe ser mayor que cero."
            );
        }

        // 2. Validar el método de pago.
        Set<String> metodosPermitidos = Set.of(
                "EFECTIVO",
                "YAPE",
                "PLIN",
                "TRANSFERENCIA",
                "TARJETA",
                "OTRO"
        );

        if (metodoPago == null
                || !metodosPermitidos.contains(metodoPago)) {
            throw new IllegalArgumentException(
                    "El método de pago no es válido."
            );
        }

        // 3. Buscar al usuario.
        Usuario usuario = buscarUsuario(idUsuario);

        // 4. Preparar el pago.
        Pago pago = new Pago();
        pago.setUsuario(usuario);
        pago.setMonto(monto);
        pago.setMetodoPago(metodoPago);
        pago.setEstado("CONFIRMADO");
        pago.setReferencia(referencia);
        pago.setObservaciones(observaciones);

        // 5. Guardar el pago.
        return pagoRepository.save(pago);
    }

    public BigDecimal obtenerMontoYaAplicado(Long idPago) {
        if (idPago == null) {
            throw new IllegalArgumentException(
                    "El ID del pago es obligatorio."
            );
        }

        Pago pago = pagoRepository.findById(idPago)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "No se encontró el pago con ID: " + idPago
                ));

        return aplicacionPagoMembresiaRepository
                .sumarAplicacionesPorPago(pago.getIdPago());
    }


    public BigDecimal calcularSaldoPendiente(Long idMembresia) {

        if (idMembresia == null) {
            throw new IllegalArgumentException(
                    "El ID de la membresía es obligatorio."
            );
        }

        Membresia membresia = membresiaRepository.findById(idMembresia)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No se encontró la membresía con ID: " + idMembresia
                ));

        BigDecimal totalAplicado =
                aplicacionPagoMembresiaRepository
                        .sumarAplicacionesPorMembresia(idMembresia);

        BigDecimal saldo = membresia.getPrecioAcordado()
                .subtract(totalAplicado);

        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "El total aplicado supera el precio de la membresía."
            );
        }

        return saldo;
    }

    @Transactional
    public AplicacionPagoMembresia aplicarPagoAMembresia(
            Long idPago,
            Long idMembresia,
            BigDecimal montoAplicado,
            TipoAplicacionPago tipoAplicacion) {

        if (tipoAplicacion == null) {
            throw new IllegalArgumentException(
                    "El tipo de aplicación es obligatorio."
            );
        }

        return aplicarPagoAMembresiaInterno(
                idPago,
                idMembresia,
                montoAplicado,
                tipoAplicacion,
                false);
    }

    @Transactional
    public AplicacionPagoMembresia aplicarPagoPosteriorAMembresia(
            Long idPago,
            Long idMembresia,
            BigDecimal montoAplicado) {

        return aplicarPagoAMembresiaInterno(
                idPago,
                idMembresia,
                montoAplicado,
                null,
                true);
    }

    private AplicacionPagoMembresia aplicarPagoAMembresiaInterno(
            Long idPago,
            Long idMembresia,
            BigDecimal montoAplicado,
            TipoAplicacionPago tipoSolicitado,
            boolean pagoPosterior) {

        // 1. Validar los datos recibidos.
        if (idPago == null || idMembresia == null) {
            throw new IllegalArgumentException(
                    "El pago y la membresía son obligatorios."
            );
        }

        if (montoAplicado == null
                || montoAplicado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "El monto aplicado debe ser mayor que cero."
            );
        }

        // Orden global de bloqueos: primero pago y luego membresia.
        Pago pago = pagoRepository.findLockedById(idPago)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No se encontro el pago con ID: " + idPago));
        if (!"CONFIRMADO".equals(pago.getEstado())) {
            throw new IllegalStateException("Solo se pueden aplicar pagos confirmados.");
        }

        Membresia membresia = membresiaRepository.findLockedById(idMembresia)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No se encontro la membresia con ID: " + idMembresia));

        if (pagoPosterior && !admitePagoPosterior(membresia.getEstado())) {
            throw new IllegalStateException(
                    "La membresia no admite pagos posteriores en estado "
                            + membresia.getEstado() + ".");
        }

        if (pagoPosterior
                && membresia.getFechaLimitePago() != null
                && LocalDate.now(reloj)
                        .isAfter(membresia.getFechaLimitePago())) {
            throw new IllegalStateException(
                    "El plazo para completar el pago de la membresia ha vencido.");
        }

        // Estas consultas son locking reads (FOR UPDATE): en MySQL leen
        // el estado actual aun si una transaccion externa ya tenia snapshot.
        List<AplicacionPagoMembresia> aplicacionesDelPago =
                aplicacionPagoMembresiaRepository.findLockedByPagoId(idPago);
        BigDecimal montoYaAplicado = aplicacionesDelPago.stream()
                .map(AplicacionPagoMembresia::getMontoAplicado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (montoYaAplicado.add(montoAplicado).compareTo(pago.getMonto()) > 0) {
            throw new IllegalArgumentException(
                    "El monto aplicado supera el importe del pago.");
        }

        List<AplicacionPagoMembresia> aplicacionesDeMembresia =
                aplicacionPagoMembresiaRepository
                        .findLockedByMembresiaId(idMembresia);
        BigDecimal totalAplicadoMembresia = aplicacionesDeMembresia.stream()
                .map(AplicacionPagoMembresia::getMontoAplicado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldoPendiente = membresia.getPrecioAcordado()
                .subtract(totalAplicadoMembresia);
        if (saldoPendiente.signum() < 0) {
            throw new IllegalStateException(
                    "El total aplicado supera el precio de la membresia.");
        }
        if (pagoPosterior && saldoPendiente.signum() == 0) {
            throw new IllegalStateException(
                    "La membresia ya se encuentra pagada.");
        }
        BigDecimal totalInicial = aplicacionesDeMembresia.stream()
                .filter(aplicacion -> aplicacion.getTipoAplicacion()
                        == TipoAplicacionPago.INICIAL)
                .map(AplicacionPagoMembresia::getMontoAplicado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // El tipo de un pago posterior se resuelve bajo bloqueo: la
        // aplicacion que consume exactamente el saldo es la FINAL.
        TipoAplicacionPago tipoAplicacion = pagoPosterior
                ? (montoAplicado.compareTo(saldoPendiente) == 0
                        ? TipoAplicacionPago.FINAL
                        : TipoAplicacionPago.INICIAL)
                : tipoSolicitado;

        if (tipoAplicacion == TipoAplicacionPago.FINAL
                && totalInicial.compareTo(new BigDecimal("40.00")) < 0) {
            throw new IllegalStateException(
                    "No se puede registrar el pago final antes de alcanzar S/ 40 iniciales.");
        }
        if (montoAplicado.compareTo(saldoPendiente) > 0) {
            throw new IllegalArgumentException(
                    "El monto aplicado supera el saldo pendiente de la membresia.");
        }
        // 10. Verificar que el pago final cubra todo el saldo.
        if (tipoAplicacion == TipoAplicacionPago.FINAL
                && montoAplicado.compareTo(saldoPendiente) != 0) {

            throw new IllegalArgumentException(
                    "El pago final debe cubrir exactamente todo el saldo pendiente."
            );
        }

        // 8. Crear la aplicación.
        AplicacionPagoMembresia aplicacion =
                new AplicacionPagoMembresia();

        aplicacion.setPago(pago);
        aplicacion.setMembresia(membresia);
        aplicacion.setMontoAplicado(montoAplicado);
        aplicacion.setTipoAplicacion(tipoAplicacion);

        // 9. Guardar la aplicación del pago.
        AplicacionPagoMembresia aplicacionGuardada =
                aplicacionPagoMembresiaRepository.saveAndFlush(aplicacion);

        // Actualizar el estado desde el total inicial recalculado bajo bloqueo.
        BigDecimal totalInicialActualizado = totalInicial;
        if (tipoAplicacion == TipoAplicacionPago.INICIAL) {
            totalInicialActualizado = totalInicial.add(montoAplicado);
        }
        if (totalInicialActualizado.compareTo(new BigDecimal("40.00")) >= 0) {
            membresia.setEstado("ACTIVA");

            // Establecer la fecha límite solo si aún no existe.
            if (membresia.getFechaLimitePago() == null) {
                membresia.setFechaLimitePago(
                        calcularFechaLimitePago(LocalDate.now(reloj))
                );
            }
        }

        // 12. Guardar los cambios de la membresía.
        membresiaRepository.save(membresia);

        return aplicacionGuardada;
    }

    private boolean admitePagoPosterior(String estado) {
        return "PENDIENTE_PAGO".equals(estado) || "ACTIVA".equals(estado);
    }

    // Calcula cinco días de servicio, sin contar los domingos. Si el pago
    // inicial ocurre un domingo (gimnasio cerrado), el conteo empieza el lunes.
    private LocalDate calcularFechaLimitePago(LocalDate fechaInicial) {
        LocalDate fechaLimite = fechaInicial;

        if (fechaLimite.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            fechaLimite = fechaLimite.plusDays(1);
        }

        int diasContados = 1;

        while (diasContados < 5) {
            fechaLimite = fechaLimite.plusDays(1);

            if (fechaLimite.getDayOfWeek()
                    != java.time.DayOfWeek.SUNDAY) {
                diasContados++;
            }
        }

        return fechaLimite;
    }


    /**
     * Compatibilidad con la tarea programada existente. La lógica real vive en
     * {@link MembresiaCicloVidaService}, que procesa cada membresía bajo
     * bloqueo y de forma idempotente.
     */
    public int cancelarMembresiasConPagoVencido() {
        return membresiaCicloVidaService
                .cancelarMembresiasConPagoVencido(LocalDate.now(reloj));
    }
}
