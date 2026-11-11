package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.dto.EstadoMembresiaRespuesta;
import com.gimnasio.sistemagym.entity.Membresia;
import com.gimnasio.sistemagym.exception.RecursoNoEncontradoException;
import com.gimnasio.sistemagym.repository.AplicacionPagoMembresiaRepository;
import com.gimnasio.sistemagym.repository.MembresiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/** Consulta del estado económico y de vigencia de una membresía. */
@Service
public class MembresiaEstadoService {

    private final MembresiaRepository membresiaRepository;
    private final AplicacionPagoMembresiaRepository aplicacionPagoRepository;
    private final Clock reloj;

    public MembresiaEstadoService(
            MembresiaRepository membresiaRepository,
            AplicacionPagoMembresiaRepository aplicacionPagoRepository,
            Clock reloj) {

        this.membresiaRepository = membresiaRepository;
        this.aplicacionPagoRepository = aplicacionPagoRepository;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public EstadoMembresiaRespuesta obtenerEstado(Long idMembresia) {
        Membresia membresia = membresiaRepository.findById(idMembresia)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se encontro la membresia con ID: " + idMembresia));

        BigDecimal totalAplicado = aplicacionPagoRepository
                .sumarAplicacionesPorMembresia(idMembresia);
        BigDecimal saldo = membresia.getPrecioAcordado().subtract(totalAplicado);

        if (saldo.signum() < 0) {
            throw new IllegalStateException(
                    "El total aplicado supera el precio de la membresía.");
        }

        return new EstadoMembresiaRespuesta(
                membresia.getIdMembresia(),
                membresia.getEstado(),
                situacion(membresia, saldo, LocalDate.now(reloj)),
                membresia.getPrecioAcordado(),
                saldo,
                saldo.signum() == 0,
                membresia.getFechaInicio(),
                membresia.getFechaVencimiento(),
                membresia.getFechaLimitePago(),
                membresia.getMotivoCancelacion(),
                membresia.getFechaCreacion());
    }

    private String situacion(Membresia membresia, BigDecimal saldo, LocalDate hoy) {
        String estado = membresia.getEstado();

        if ("CANCELADA".equals(estado)) {
            return "CANCELADA_POR_FALTA_DE_PAGO";
        }
        if ("VENCIDA".equals(estado)) {
            return "VENCIDA_POR_VIGENCIA";
        }
        if ("ACTIVA".equals(estado)) {
            if (saldo.signum() == 0) {
                return "ACTIVA_PAGADA";
            }
            if (membresia.getFechaLimitePago() != null
                    && hoy.isAfter(membresia.getFechaLimitePago())) {
                return "PENDIENTE_FUERA_DE_PLAZO";
            }
            return "PENDIENTE_DENTRO_DEL_PLAZO";
        }
        if ("PENDIENTE_PAGO".equals(estado)) {
            return "PENDIENTE_DENTRO_DEL_PLAZO";
        }
        return estado;
    }
}
