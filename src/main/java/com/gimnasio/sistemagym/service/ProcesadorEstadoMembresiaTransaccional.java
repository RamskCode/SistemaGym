package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.entity.AplicacionPagoMembresia;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Aplica, siempre dentro de una transacción y con bloqueo pesimista, los
 * cambios de estado automáticos de una membresía. Cada membresía se procesa
 * de forma independiente para que un fallo no revierta las demás.
 */
@Service
public class ProcesadorEstadoMembresiaTransaccional {

    public static final String MOTIVO_FALTA_DE_PAGO =
            "No se completó el pago dentro del plazo establecido.";

    private final MembresiaRepository membresiaRepository;
    private final AplicacionPagoMembresiaRepository aplicacionPagoRepository;

    public ProcesadorEstadoMembresiaTransaccional(
            MembresiaRepository membresiaRepository,
            AplicacionPagoMembresiaRepository aplicacionPagoRepository) {

        this.membresiaRepository = membresiaRepository;
        this.aplicacionPagoRepository = aplicacionPagoRepository;
    }

    /**
     * Cancela una membresía ACTIVA cuyo plazo de pago ya terminó y que aún
     * conserva saldo pendiente. Recalcula el saldo bajo bloqueo y no hace nada
     * si la membresía ya fue cancelada, pagada o su plazo sigue vigente.
     */
    @Transactional
    public boolean cancelarSiCorresponde(Long idMembresia, LocalDate hoy) {
        Membresia membresia = bloquear(idMembresia);

        if (!"ACTIVA".equals(membresia.getEstado())) {
            return false;
        }
        if (membresia.getFechaLimitePago() == null
                || !membresia.getFechaLimitePago().isBefore(hoy)) {
            return false;
        }

        BigDecimal saldo = saldoPendienteBloqueado(membresia);

        if (saldo.signum() == 0) {
            return false;
        }
        if (saldo.signum() < 0) {
            throw new IllegalStateException(
                    "El total aplicado supera el precio de la membresía.");
        }

        membresia.setEstado("CANCELADA");
        membresia.setMotivoCancelacion(MOTIVO_FALTA_DE_PAGO);
        membresiaRepository.saveAndFlush(membresia);
        return true;
    }

    /**
     * Marca como VENCIDA una membresía ACTIVA completamente pagada cuya
     * vigencia ya terminó. No confunde el vencimiento natural con la
     * cancelación por deuda.
     */
    @Transactional
    public boolean marcarVencidaSiCorresponde(Long idMembresia, LocalDate hoy) {
        Membresia membresia = bloquear(idMembresia);

        if (!"ACTIVA".equals(membresia.getEstado())) {
            return false;
        }
        if (membresia.getFechaVencimiento() == null
                || !membresia.getFechaVencimiento().isBefore(hoy)) {
            return false;
        }

        BigDecimal saldo = saldoPendienteBloqueado(membresia);

        if (saldo.signum() != 0) {
            return false;
        }

        membresia.setEstado("VENCIDA");
        membresia.setMotivoCancelacion(null);
        membresiaRepository.saveAndFlush(membresia);
        return true;
    }

    private Membresia bloquear(Long idMembresia) {
        return membresiaRepository.findLockedById(idMembresia)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No se encontró la membresía con ID: " + idMembresia));
    }

    private BigDecimal saldoPendienteBloqueado(Membresia membresia) {
        List<AplicacionPagoMembresia> aplicaciones =
                aplicacionPagoRepository.findLockedByMembresiaId(
                        membresia.getIdMembresia());

        BigDecimal totalAplicado = aplicaciones.stream()
                .map(AplicacionPagoMembresia::getMontoAplicado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return membresia.getPrecioAcordado().subtract(totalAplicado);
    }
}
