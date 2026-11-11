package com.gimnasio.sistemagym.service;

import com.gimnasio.sistemagym.repository.MembresiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Orquesta el ciclo de vida automático de las membresías: cancelación por
 * falta de pago y vencimiento natural. El proceso es idempotente porque cada
 * membresía se vuelve a leer y validar bajo bloqueo antes de modificarse.
 */
@Service
public class MembresiaCicloVidaService {

    private final MembresiaRepository membresiaRepository;
    private final ProcesadorEstadoMembresiaTransaccional procesador;
    private final Clock reloj;

    public MembresiaCicloVidaService(
            MembresiaRepository membresiaRepository,
            ProcesadorEstadoMembresiaTransaccional procesador,
            @Qualifier("relojMembresias") Clock reloj) {

        this.membresiaRepository = membresiaRepository;
        this.procesador = procesador;
        this.reloj = reloj;
    }

    public ResultadoCicloVida procesarCicloVida() {
        LocalDate hoy = LocalDate.now(reloj);
        return new ResultadoCicloVida(
                cancelarMembresiasConPagoVencido(hoy),
                marcarMembresiasVencidas(hoy),
                hoy);
    }

    public int cancelarMembresiasConPagoVencido(LocalDate hoy) {
        List<Long> candidatas = membresiaRepository
                .findIdsByEstadoAndFechaLimitePagoBefore("ACTIVA", hoy);

        int canceladas = 0;
        for (Long idMembresia : candidatas) {
            if (procesador.cancelarSiCorresponde(idMembresia, hoy)) {
                canceladas++;
            }
        }
        return canceladas;
    }

    public int marcarMembresiasVencidas(LocalDate hoy) {
        List<Long> candidatas = membresiaRepository
                .findIdsByEstadoAndFechaVencimientoBefore("ACTIVA", hoy);

        int vencidas = 0;
        for (Long idMembresia : candidatas) {
            if (procesador.marcarVencidaSiCorresponde(idMembresia, hoy)) {
                vencidas++;
            }
        }
        return vencidas;
    }

    public record ResultadoCicloVida(
            int canceladas, int vencidas, LocalDate fecha) {
    }
}
